# Spec: Print API & Job Queue

## Overview

REST API to queue print jobs. Backend executor polls queue, renders labels, sends to printer driver. Handles offline printers gracefully (jobs wait, retry on recovery).

## Data Model

**Entity: PrintJob**
```java
@Entity
@Table(name = "print_job")
public class PrintJob {
  @Id private UUID id;
  
  @Column(nullable = false) private String itemType;  // "box", "batch", "product"
  @Column(nullable = false) private UUID itemId;      // Box/Batch/Product UUID
  @Column(nullable = false) private int copies;       // how many labels (default 1)
  
  @Column(nullable = false) private String status;    // "queued", "printing", "done", "failed"
  @Column(columnDefinition = "TEXT") private String error;  // error message if failed
  
  @CreationTimestamp private LocalDateTime createdAt;
  @UpdateTimestamp private LocalDateTime updatedAt;
  
  // getters, setters, constructor
}
```

**Repository:**
```java
public interface PrintJobRepository extends JpaRepository<PrintJob, UUID> {
  List<PrintJob> findByStatusOrderByCreatedAtAsc(String status);
  List<PrintJob> findByStatusAndUpdatedAtAfter(String status, LocalDateTime since);
}
```

## REST Endpoints

### POST /api/print-jobs — Queue a Print Job

**Request:**
```json
{
  "itemType": "box",
  "itemId": "550e8400-e29b-41d4-a716-446655440000",
  "copies": 1
}
```

**Validation:**
- `itemType` must be one of: "box", "batch", "product"
- `itemId` must be a valid UUID and exist in the database (Box, Batch, or Product table per itemType)
- `copies` must be > 0 and <= 100 (prevent DoS)

**Response (HTTP 201):**
```json
{
  "jobId": "660e8400-e29b-41d4-a716-446655440001",
  "status": "queued",
  "itemType": "box",
  "itemId": "550e8400-e29b-41d4-a716-446655440000",
  "copies": 1,
  "error": null
}
```

**Error Responses:**
- 400 Bad Request: itemType invalid, copies out of range, validation fails
- 404 Not Found: itemId doesn't exist
- 500 Internal Server Error: database error

**Behavior:**
- Create PrintJob with status="queued"
- Return immediately (don't wait for print to complete)
- Executor picks it up from queue within 500ms

### GET /api/print-jobs/{jobId} — Poll Job Status

**Response (HTTP 200):**
```json
{
  "jobId": "660e8400-e29b-41d4-a716-446655440001",
  "status": "done",
  "itemType": "box",
  "itemId": "550e8400-e29b-41d4-a716-446655440000",
  "copies": 1,
  "error": null,
  "createdAt": "2026-07-27T10:23:45Z",
  "updatedAt": "2026-07-27T10:23:47Z"
}
```

**Possible statuses:**
- `queued` — waiting to print (job just created)
- `printing` — executor currently rendering and sending to printer
- `done` — successfully printed all copies
- `failed` — printer error or max retries exceeded; check `error` field

**Error Responses:**
- 404 Not Found: jobId doesn't exist
- 500 Internal Server Error: database error

**Behavior:**
- Frontend polls this every 500ms while modal is open
- Modal closes on status="done" or "failed"
- If failed, show error message from `error` field

## Print Executor

**Service: PrintExecutorService**

Runs every 500ms (scheduled via `@Scheduled(fixedRate = 500)`). Logic:

```
1. Find all PrintJobs with status="queued" (ordered by createdAt ASC)
2. For each job (up to 5 per tick, to avoid overload):
   a. Set status="printing"
   b. Fetch the item (Box/Batch/Product from DB)
   c. Render label via LabelTemplateService
   d. Call PrinterDriver.sendLabel(zpl, copies)
   e. If success: set status="done", update timestamp
   f. If failure: 
      - Increment retry counter (stored in PrintJob or separate retry table)
      - If retries < 10: leave status="queued", will retry next tick
      - If retries >= 10: set status="failed", store error message
```

**Retry Logic:**
```java
@Scheduled(fixedRate = 500)
public void executePrintQueue() {
  List<PrintJob> queued = printJobRepository.findByStatusOrderByCreatedAtAsc("queued");
  
  for (PrintJob job : queued.stream().limit(5).toList()) {
    job.setStatus("printing");
    printJobRepository.save(job);
    
    try {
      Object item = fetchItem(job.getItemType(), job.getItemId());
      String zpl = labelService.renderLabel(item);
      printerDriver.sendLabel(zpl, job.getCopies());
      
      job.setStatus("done");
      job.setError(null);
    } catch (PrinterNotFoundException | TimeoutException e) {
      // Printer unreachable or timeout — retry later
      int retries = getRetryCount(job.getId());
      if (retries < 10) {
        job.setStatus("queued");  // back to queue for next tick
        incrementRetry(job.getId());
      } else {
        job.setStatus("failed");
        job.setError("Printer unreachable after 10 retries: " + e.getMessage());
      }
    } catch (Exception e) {
      // Other error (invalid template, item not found, etc.)
      job.setStatus("failed");
      job.setError(e.getMessage());
    }
    
    printJobRepository.save(job);
  }
}
```

**Retry Counter Table (optional, or store in PrintJob as `retryCount` int):**
```sql
CREATE TABLE print_job_retry (
  job_id UUID PRIMARY KEY REFERENCES print_job(id),
  retry_count INT NOT NULL DEFAULT 0
);
```

## Printer Driver

**Interface: PrinterDriver**

```java
public interface PrinterDriver {
  void sendLabel(String zpl, int copies) throws PrinterException;
  PrinterStatus getStatus() throws PrinterException;
}
```

**Implementation: UsbPrinterDriver** (or NetworkPrinterDriver)

```java
@Component
public class UsbPrinterDriver implements PrinterDriver {
  private final PrinterConfig config;  // injected, loaded from DB
  private final SerialPort port;       // jssc SerialPort
  
  public void sendLabel(String zpl, int copies) throws PrinterException {
    if (!port.isOpened()) {
      throw new PrinterException("Printer not connected");
    }
    
    String fullZpl = zpl + "\n^XZ\n".repeat(copies);  // repeat label copies
    
    try {
      port.writeBytes(fullZpl.getBytes());
      // Wait for printer to finish (100ms per copy, rough estimate)
      Thread.sleep(100 * copies);
    } catch (SerialPortException e) {
      throw new PrinterException("Failed to send to printer: " + e.getMessage());
    }
  }
  
  public PrinterStatus getStatus() throws PrinterException {
    if (!port.isOpened()) {
      throw new PrinterException("Port closed");
    }
    return PrinterStatus.OK;  // simplified; could poll printer status byte
  }
}
```

**Initialization:**
- On app startup, read PrinterConfig from DB
- Connect to printer (USB or network)
- If connection fails: log warning, PrinterDriver throws exception on sendLabel (caught by executor)

## Frontend API Integration

**api.ts additions:**
```ts
export const printer = {
  queueJob: (itemType: string, itemId: string, copies: number) =>
    post<{jobId: string, status: string}>('/api/print-jobs', {
      itemType,
      itemId,
      copies,
    }),

  getJobStatus: (jobId: string) =>
    get<{jobId: string, status: string, error: string | null}>(
      `/api/print-jobs/${jobId}`
    ),
}
```

**Usage Pattern (in PrintModal.tsx):**
```ts
const [jobId, setJobId] = useState<string | null>(null);
const [status, setStatus] = useState<string>('');

const handlePrint = async () => {
  try {
    const resp = await printer.queueJob(itemType, itemId, copies);
    setJobId(resp.jobId);
    setStatus('queued');
    
    // Poll every 500ms
    const interval = setInterval(async () => {
      const job = await printer.getJobStatus(resp.jobId);
      setStatus(job.status);
      
      if (job.status === 'done') {
        clearInterval(interval);
        // Close modal, show success banner
      } else if (job.status === 'failed') {
        clearInterval(interval);
        // Show error banner with job.error
      }
    }, 500);
  } catch (err) {
    // Network error, show error banner
  }
};
```

## Acceptance Criteria

✅ POST /api/print-jobs creates a PrintJob and returns jobId immediately
✅ PrintJob status persists in database across app restarts
✅ Executor polls every 500ms and picks up queued jobs
✅ Label renders without error (template + data valid)
✅ Printer sends label via USB/network (or simulated in test)
✅ On success: status transitions queued → printing → done
✅ On printer offline: status stays "queued", executor retries for 10 ticks (~5 seconds)
✅ After 10 retries: status="failed", error message stored
✅ GET /api/print-jobs/{jobId} returns current status and error (if failed)
✅ Frontend polls job status and updates modal accordingly
✅ Multiple jobs queue without blocking (executor handles 5+ jobs per tick)
✅ No data loss: queue persists on app crash
