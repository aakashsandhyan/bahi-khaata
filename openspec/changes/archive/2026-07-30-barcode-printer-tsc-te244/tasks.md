# Tasks: TSC TE-244 Barcode Printer Integration

## Section 1: Database & Backend Setup

### 1.1 Create Flyway Migration for PrintJob Table
- File: `backend/src/main/resources/db/migration/V<N>__create_print_job_table.sql`
- Create `print_job` table with columns: id (UUID PK), itemType, itemId, copies, status, error, createdAt, updatedAt
- Add index on status column for executor queries
- Verify migration runs without error on `gradlew bootRun`

### 1.2 Create Flyway Migration for PrinterConfig Table
- File: `backend/src/main/resources/db/migration/V<N+1>__create_printer_config_table.sql`
- Create `printer_config` table with columns: id (UUID PK), address, portSpeed, paperSize, copiesDefault, enabled, testStatus, lastTestedAt, testError, createdAt, updatedAt
- Verify migration runs

## Section 2: Backend Entities & Repositories

### 2.1 Create PrintJob Entity
- File: `backend/src/main/java/com/bahikhaata/backend/print/PrintJob.java`
- JPA @Entity with fields from spec
- Constructors: default, full
- Getters/setters for all fields
- @CreationTimestamp, @UpdateTimestamp for timestamps
- No special business logic (plain data class)

### 2.2 Create PrinterConfig Entity
- File: `backend/src/main/java/com/bahikhaata/backend/print/PrinterConfig.java`
- JPA @Entity with fields from spec
- Constructors: default, full
- Getters/setters for all fields
- Add static method: `getSingletonId()` returns a fixed UUID (for singleton pattern)

### 2.3 Create PrintJobRepository
- File: `backend/src/main/java/com/bahikhaata/backend/print/PrintJobRepository.java`
- Interface extends JpaRepository<PrintJob, UUID>
- Method: `findByStatusOrderByCreatedAtAsc(String status) → List<PrintJob>`
- Method: `findByStatusAndUpdatedAtAfter(String status, LocalDateTime since) → List<PrintJob>` (for monitoring)

### 2.4 Create PrinterConfigRepository
- File: `backend/src/main/java/com/bahikhaata/backend/print/PrinterConfigRepository.java`
- Interface extends JpaRepository<PrinterConfig, UUID>
- Method: `findBySingleton() → Optional<PrinterConfig>` or custom query for id = singleton UUID

## Section 3: Label Rendering & Printer Driver

### 3.1 Create PrintLabelRequest DTO
- File: `contracts/src/main/java/com/bahikhaata/contracts/PrintLabelRequest.java`
- Record: barcode, productName, category, costPerUnit, mrpPaise, lotId, expiryDate, receivedDate
- Add validation: all fields non-empty, barcode scannable
- Add method: `toMap() → Map<String, Object>` for FreeMarker

### 3.2 Create LabelTemplateService
- File: `backend/src/main/java/com/bahikhaata/backend/print/LabelTemplateService.java`
- Inject FreeMarker Configuration (or create one)
- Method: `renderLabel(PrintLabelRequest) → String` returns ZPL
- Handle template not found gracefully (clear error message)

### 3.3 Create FreeMarker Label Template
- File: `backend/src/main/resources/print-templates/label.zpl`
- ZPL template with placeholders for barcode, product name, category, cost, MRP, lot, date
- Test template renders without FreeMarker errors
- No hardcoded values (all via template variables)

### 3.4 Create PrinterDriver Interface
- File: `backend/src/main/java/com/bahikhaata/backend/print/PrinterDriver.java`
- Interface with methods: `sendLabel(zpl, copies)`, `getStatus()`
- Custom exception: PrinterException (extends RuntimeException)

### 3.5 Create UsbPrinterDriver Implementation
- File: `backend/src/main/java/com/bahikhaata/backend/print/UsbPrinterDriver.java`
- Implements PrinterDriver
- Constructor: inject PrinterConfig (or load from DB)
- `sendLabel(zpl, copies)`: open serial port, write bytes, wait, close
- `getStatus()`: return OK or throw PrinterException
- Add jssc dependency to build.gradle if not present

### 3.6 Create PrinterConnectionTester
- File: `backend/src/main/java/com/bahikhaata/backend/print/PrinterConnectionTester.java`
- Method: `testConnection(address, portSpeed) → TestResult`
- Support both USB (/dev/ttyUSB0) and network (IP:port) addresses
- 5-second timeout for network connections
- Return TestResult record with status (OK/UNREACHABLE/ERROR) and message

## Section 4: API Endpoints

### 4.1 Create Print Controller DTOs
- File: `contracts/src/main/java/com/bahikhaata/contracts/QueuePrintJobRequest.java`
  - Record: itemType, itemId, copies
- File: `contracts/src/main/java/com/bahikhaata/contracts/QueuePrintJobResponse.java`
  - Record: jobId, status, itemType, itemId, copies, error
- File: `contracts/src/main/java/com/bahikhaata/contracts/PrintJobStatusResponse.java`
  - Record: jobId, status, itemType, itemId, copies, error, createdAt, updatedAt

### 4.2 Create PrintController
- File: `backend/src/main/java/com/bahikhaata/backend/print/PrintController.java`
- Endpoint: `POST /api/print-jobs` → queue job, return jobId immediately
  - Validate itemType exists and itemId is valid
  - Create PrintJob with status="queued"
  - Return 201 with job details
- Endpoint: `GET /api/print-jobs/{jobId}` → get job status
  - Return current status, error message if failed
  - Return 404 if jobId not found

### 4.3 Create Admin Printer Config Controller
- File: `backend/src/main/java/com/bahikhaata/backend/admin/PrinterConfigController.java`
- Mark as admin-only: `@PreAuthorize("hasRole('ADMIN')"`
- Endpoint: `GET /api/admin/printer-config` → return singleton config (or 404)
- Endpoint: `PUT /api/admin/printer-config` → update config
  - Validate all fields
  - Create if doesn't exist, update if exists
  - Don't clear testStatus
  - Return updated config
- Endpoint: `POST /api/admin/printer-config/test` → test printer
  - Call PrinterConnectionTester
  - Update config with testStatus and testError
  - Return {testStatus, message, testedAt}

## Section 5: Print Executor & Background Service

### 5.1 Create PrintExecutorService
- File: `backend/src/main/java/com/bahikhaata/backend/print/PrintExecutorService.java`
- Inject: PrintJobRepository, PrinterConfigRepository, LabelTemplateService, PrinterDriver, PrinterConnectionTester
- Scheduled method: `@Scheduled(fixedRate = 500)` executePrintQueue()
  - Find all jobs with status="queued" (limit 5)
  - For each job: try to render label, send to printer
  - On success: status="done", error=null
  - On timeout/unreachable: increment retry counter, if < 10 stay "queued", else status="failed"
  - On other error: status="failed", store error message
- Add retry counter logic (either in PrintJob field or separate table)

### 5.2 Add Retry Counter to PrintJob
- Add field: `int retryCount` (default 0) to PrintJob entity
- Update migration to add column: `retry_count INT DEFAULT 0`
- Reset retryCount when job moves to "queued" after failed attempt

### 5.3 Create Initialization Hook
- On app startup (in a @PostConstruct method):
  - Check if PrinterConfig exists (if not, create default)
  - Load PrinterConfig
  - Initialize PrinterDriver with config address/portSpeed
  - Don't fail if printer unreachable (executor will retry)

## Section 6: Frontend Types & API

### 6.1 Update types.ts
- Add type: `PrintJob` (jobId, status, itemType, itemId, copies, error, createdAt, updatedAt)
- Add type: `PrinterConfig` (address, portSpeed, paperSize, copiesDefault, enabled, testStatus, lastTestedAt, testError)

### 6.2 Update api.ts
- Add `printer` export object:
  - `queueJob(itemType, itemId, copies) → post to /api/print-jobs`
  - `getJobStatus(jobId) → get from /api/print-jobs/{jobId}`
  - `getConfig() → get from /api/admin/printer-config`
  - `saveConfig(config) → put to /api/admin/printer-config`
  - `testPrinter() → post to /api/admin/printer-config/test`

## Section 7: Frontend Components

### 7.1 Create PrintModal Component
- File: `dashboard/web/src/PrintModal.tsx`
- Props: itemType, itemId, itemName, onClose, onSuccess
- State: copies, jobId, status, message
- Render:
  - Modal overlay
  - Item details (name, category, cost, MRP)
  - Input for copies (default from config)
  - "Print" and "Cancel" buttons
- Behavior:
  - On mount: fetch printer config and load copiesDefault
  - On Print: queue job via `printer.queueJob()`, get jobId
  - Poll status every 500ms via `printer.getJobStatus()`
  - On status="done": show success, close after 2 seconds
  - On status="failed": show error message, allow retry
  - On Cancel: close modal

### 7.2 Create PrinterConfig Admin Screen
- File: `dashboard/web/src/admin/PrinterConfig.tsx`
- State: config, formData, testLoading, saveLoading, message
- On mount: `printer.getConfig()` → load into formData
- Render:
  - Status badge (green if testStatus="OK", red otherwise, with message)
  - Form inputs: address, portSpeed, copiesDefault, enabled checkbox
  - Buttons: "Test Print", "Save Config"
- Behavior:
  - Test button: `printer.testPrinter()` → show spinner → update testStatus badge
  - Save button: `printer.saveConfig()` → show success banner
  - Error handling: show banner for validation/network errors

### 7.3 Add Print Button to Receiving.tsx
- In detail view (when selectedLot is open):
  - For each box in the list: add "Print Label" button next to box name
  - On click: open PrintModal with itemType="box", itemId=box.id
  - Close modal on success

### 7.4 Add Print Button to Unpacking.tsx
- In detail view (after batch counted):
  - Add "Print Labels" button next to "Finish" button
  - On click: open PrintModal with itemType="batch", itemId=box.id, quantity=counted quantity
  - Allow user to set copies (default 1)

### 7.5 Update App.tsx Navigation
- Add "Printer Config" link in admin section (if admin nav exists)
- Route: `/admin/printer-config` → `<PrinterConfig />`

## Section 8: Testing & Verification

### 8.1 Unit Test: LabelTemplateService
- Test: template renders without errors for all 3 item types (box, batch, product)
- Test: ZPL output contains expected fields (barcode, name, cost, MRP, lot, date)
- Test: null fields handled gracefully (omitted from output, no error)

### 8.2 Unit Test: PrinterConnectionTester
- Test: network connection (mock Socket, verify connect called with correct host:port)
- Test: USB connection (mock SerialPort, verify openPort called)
- Test: timeout returns UNREACHABLE status
- Test: port error returns ERROR status

### 8.3 Integration Test: PrintExecutor
- Test: job queued → executor picks up and renders label → status="done"
- Test: job queued → printer offline → status stays "queued" → retries happen
- Test: 10 retries exhausted → status="failed", error message stored
- Test: multiple jobs processed without blocking

### 8.4 Manual Test: Full Workflow
- Printer connected via USB or network
- Open Receiving, select a lot, open detail
- Click "Print Label" on a box → modal opens
- Confirm label prints on TSC TE-244
- Check backend PrintJob table: status="done"
- Repeat in Unpacking: click "Print Labels" on counted batch
- Verify label printed with correct product name, category, cost, MRP

### 8.5 Manual Test: Offline Fallback
- Stop printer (unplug USB or disable network)
- Try to print → modal shows "Printing..." spinner
- Spinner continues for ~5 seconds (retries)
- Reconnect printer → label prints automatically
- Check PrintJob status: "done"

### 8.6 Manual Test: Admin Config
- Navigate to /admin/printer-config
- Edit address (e.g., change IP)
- Click "Test Print" → status badge updates (OK or error)
- Change copies default
- Click "Save Config"
- Refresh page → form still shows saved values
- Try to print → uses new copiesDefault

## Section 9: Cleanup & Documentation

### 9.1 Code Review
- All Java classes follow project conventions (naming, spacing, imports)
- All React components use shared `api.ts` and `types.ts` (no raw fetch)
- Error messages are user-friendly (no stack traces in UI)
- No debug logs left in production code

### 9.2 Database Verification
- All migrations applied successfully (`./gradlew bootRun` starts clean)
- All tables created with correct column types and indexes
- No orphaned rows in print_job or printer_config tables

### 9.3 Build & Runtime
- `./gradlew build` passes all tests
- `npm run build` (frontend) compiles without errors
- App starts without errors: `./gradlew bootRun`
- No console warnings about missing dependencies

### 9.4 Update CLAUDE.md (if needed)
- Note TSC TE-244 integration complete
- Printer config in DB, PrinterDriver injectable via Spring

## Totals
- 9 sections
- ~40 tasks
- Estimated sequence: Database → Entities → Label engine → API → Executor → Frontend types → Frontend screens → Testing → Cleanup
