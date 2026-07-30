# TSC TE-244 Barcode Printer Integration — Design

## Context

Manual labeling at goods-in, unpacking, and catalog slows operations and loses traceability. TSC TE-244 is a desktop thermal printer; integration adds print-on-demand for 4x6 labels tied to inventory events.

## Key Decisions

### 1. Label Format: 4x6 Thermal, Code128 Barcode + Human-Readable Fields

**What goes on a label:**
- **Barcode** (top): Code128 encoding the product code (or generated system ID if code missing)
- **Product name**: Bold, left-aligned, 1–2 lines
- **Category**: Smaller text, neutral tone
- **Cost breakdown** (aligned right):
  - Cost per unit (₹)
  - MRP (if known)
- **Lot/batch identifier** (if applicable): top-right, small
- **Expiry date** (if applicable): bottom-right, small

**Why Code128**: barcode guns in shops decode it natively; no extra config needed. QR optional for future (e.g., link to product detail on mobile) but not MVP.

**Why 4x6**: standard thermal label size, TSC TE-244 native, fits on shelf labels and box stickers.

### 2. Print Backend: Job Queue, Async Execution, Offline Fallback

**Architecture:**
- REST API endpoint: `POST /api/print-jobs`
- Request: `{itemType: "box" | "batch" | "product", itemId, quantity: 1, copies: 1}`
- Response: `{jobId, status: "queued" | "printing" | "done" | "failed"}`
- Backend maintains a `PrintJob` queue (JPA entity) with `status`, `itemId`, `error` fields
- Executor polls queue every 500ms, fires labels to TSC TE-244 via USB or network
- If printer unreachable: job stays queued, retries continue (operator can check queue later, printer offline doesn't block the UI)

**Label template rendering:**
- Template engine (e.g., FreeMarker or Velocity) renders SVG or ZPL (Zebra Programming Language)
- ZPL is native to TSC TE-244, avoids raster overhead
- Template lives in `backend/resources/print-templates/` (per-item-type)
- At print time: fetch item data (product, box, batch), render template, send to printer driver

**Printer communication:**
- Library: `jssc` (Java Simple Serial Connector) for USB, or HTTP POST to network-attached TSC box
- Config stored in DB: printer address (IP or serial port), copies default, paper size
- Health check: test connectivity before queueing (UI shows green/red status on config screen)

### 3. Printer Config: Admin-Only Screen with Test Print

**New entity: `PrinterConfig`**
- Fields: `id`, `address` (IP or serial), `portSpeed`, `paperSize` ("4x6" default), `copiesDefault`, `enabled`, `lastTestedAt`, `testStatus` (enum: OK, UNREACHABLE, ERROR)
- Singleton (one config per install, not per-user)

**Admin screen: `PrinterConfig.tsx`**
- Text input for printer address (default: `192.168.1.100:9100` or `/dev/ttyUSB0`)
- Number input for port speed / copies default
- Dropdown for paper size
- Button: "Test Print" → prints a sample label, updates `testStatus`
- Status badge: green if `testStatus == OK`, red otherwise, with error message if recent
- Save button: `PUT /api/admin/printer-config`

**Endpoints:**
- `GET /api/admin/printer-config` — return config + test status
- `PUT /api/admin/printer-config` — update config
- `POST /api/admin/printer-config/test` — fire test label, update testStatus

### 4. Frontend Print Buttons: Reuse Pattern from Unpacking

**Pattern:** Add "Print Label(s)" button wherever a box, batch, or product is named:

**In Receiving.tsx** (goods-in workflow):
- After a box is received → "Print Label" button next to box name in the list
- Click → single label for that box printed
- Feedback: inline banner "Label 1 printed" or "Print failed: printer offline"

**In Unpacking.tsx** (unpacking workflow):
- After a batch is counted → "Print Labels" button (qty = batch quantity)
- Click → modal with options: print 1 label or qty labels (one per unit)
- Feedback: banner "Printed X labels" or error

**In Catalog** (pricing screen):
- Bulk action: select multiple products → "Print Price Labels"
- Modal: show selected products, input copies, print
- Feedback: banner with count

**UI component: `PrintModal.tsx`**
- Modal: show item (product name, category, cost, MRP)
- Input: # of copies (default from config)
- Button: "Print" → calls `receiving.printLabel(itemType, itemId, copies)`
- Status while printing: "Printing..." with spinner
- On success/error: close modal, show banner in parent

### 5. Workflow Integration

**Goods-in receiving:**
```
Box arrives → Scan carton ID → [Receive button]
                                     ↓
                        Box state = RECEIVED
                                     ↓
                        Show "Print Label" button
                                     ↓
                        [Print] → label queued → printer executes
```

**Unpacking:**
```
Count batch → [Finish button]
                   ↓
        Batch recorded, line moved to UNPACKED
                   ↓
        Show "Print Labels" button
                   ↓
        [Print] → labels queued (qty copies)
```

**Catalog:**
```
Price changes approved → [Print Price Tags] bulk action
                               ↓
                   Modal: select products + copies
                               ↓
                   [Print] → one label per product × copies
```

## API Changes

**New REST endpoints:**

```
POST /api/print-jobs
  Body: {itemType: "box" | "batch" | "product", itemId: UUID, quantity: 1, copies: 1}
  Response: {jobId: UUID, status: string}

GET /api/print-jobs/{jobId}
  Response: {jobId, status, itemId, itemType, copies, error: string | null, createdAt, updatedAt}

GET /api/admin/printer-config
  Response: {address, portSpeed, paperSize, copiesDefault, enabled, testStatus, lastTestedAt}

PUT /api/admin/printer-config
  Body: {address, portSpeed, paperSize, copiesDefault, enabled}
  Response: {id, ...}

POST /api/admin/printer-config/test
  Response: {testStatus, message}
```

## Frontend Changes

**New types (types.ts):**
```ts
export interface PrintJob {
  jobId: string
  status: 'queued' | 'printing' | 'done' | 'failed'
  itemType: 'box' | 'batch' | 'product'
  itemId: string
  copies: number
  error: string | null
}

export interface PrinterConfig {
  address: string
  portSpeed: number
  paperSize: string
  copiesDefault: number
  enabled: boolean
  testStatus: 'OK' | 'UNREACHABLE' | 'ERROR'
  lastTestedAt: string | null
}
```

**New API methods (api.ts):**
```ts
export const printer = {
  queueJob: (itemType: string, itemId: string, quantity: number, copies: number) =>
    post<PrintJob>('/api/print-jobs', { itemType, itemId, quantity, copies }),
  getJob: (jobId: string) =>
    get<PrintJob>(`/api/print-jobs/${jobId}`),
  getConfig: () =>
    get<PrinterConfig>('/api/admin/printer-config'),
  saveConfig: (config: PrinterConfig) =>
    put<PrinterConfig>('/api/admin/printer-config', config),
  testPrint: () =>
    post<{testStatus: string, message: string}>('/api/admin/printer-config/test'),
}
```

**New components:**
- `PrinterConfig.tsx` — admin config screen
- `PrintModal.tsx` — modal for print options (copies, confirmation)
- Print buttons added to `Receiving.tsx`, `Unpacking.tsx`, Catalog detail view

## Data Model

**New JPA entities:**

```java
@Entity
@Table(name = "print_job")
public class PrintJob {
  @Id private UUID id;
  private String itemType;     // "box", "batch", "product"
  private UUID itemId;
  private int copies;
  private String status;       // "queued", "printing", "done", "failed"
  private String error;        // null if success
  @CreationTimestamp private LocalDateTime createdAt;
  @UpdateTimestamp private LocalDateTime updatedAt;
}

@Entity
@Table(name = "printer_config")
public class PrinterConfig {
  @Id private UUID id;
  private String address;      // IP:port or /dev/ttyUSB0
  private int portSpeed;       // e.g., 9600
  private String paperSize;    // "4x6"
  private int copiesDefault;   // 1
  private boolean enabled;
  private String testStatus;   // "OK", "UNREACHABLE", "ERROR"
  private LocalDateTime lastTestedAt;
}
```

## Migrations

```sql
-- V<N>__add_print_job_table.sql
CREATE TABLE print_job (
  id UUID PRIMARY KEY,
  item_type VARCHAR(50) NOT NULL,
  item_id UUID NOT NULL,
  copies INT NOT NULL,
  status VARCHAR(50) NOT NULL,
  error TEXT,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_print_job_status ON print_job(status);

-- V<N+1>__add_printer_config_table.sql
CREATE TABLE printer_config (
  id UUID PRIMARY KEY,
  address VARCHAR(255) NOT NULL,
  port_speed INT NOT NULL,
  paper_size VARCHAR(10) NOT NULL,
  copies_default INT NOT NULL,
  enabled BOOLEAN NOT NULL,
  test_status VARCHAR(50),
  last_tested_at TIMESTAMP
);
```

## Mockups

### Printer Config Screen

```
┌─────────────────────────────────────────────────────────┐
│  Printer Configuration                                  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Status: ● OK (tested 2 min ago)                        │
│  [Test Print]                                           │
│                                                         │
│  ─────────────────────────────────────────────────────  │
│                                                         │
│  Printer Address                                        │
│  ┌──────────────────────────────────────────────────┐   │
│  │ 192.168.1.100:9100                              │   │
│  └──────────────────────────────────────────────────┘   │
│  (IP:port for network, or /dev/ttyUSB0 for USB)        │
│                                                         │
│  Port Speed                Copies Default               │
│  ┌──────────────┐          ┌──────────────┐            │
│  │ 9600         │          │ 1            │            │
│  └──────────────┘          └──────────────┘            │
│                                                         │
│  Paper Size                                             │
│  ┌──────────────────────────────────────────────────┐   │
│  │ 4x6                                              │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  ⬜ Enable Printer                                      │
│                                                         │
│  ┌──────────────┐         ┌──────────────┐             │
│  │ [   Save    ]│         │ [Test Print] │             │
│  └──────────────┘         └──────────────┘             │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Print Modal (triggered from Receiving/Unpacking)

```
┌──────────────────────────────────────────┐
│  Print Label                          ✕  │
├──────────────────────────────────────────┤
│                                          │
│  Product: Coconut Oil 1L                 │
│  Category: Kitchen                       │
│  Cost: ₹240 | MRP: ₹450                  │
│                                          │
│  ──────────────────────────────────────  │
│                                          │
│  Number of Copies                        │
│  ┌──────────────────────────────────┐    │
│  │ 1                                │    │
│  └──────────────────────────────────┘    │
│                                          │
│  ┌──────────────┐  ┌──────────────┐     │
│  │  [  Print   ]│  │  [ Cancel  ] │     │
│  └──────────────┘  └──────────────┘     │
│                                          │
│                                          │
│  Printing...    ⟳                        │
│                                          │
└──────────────────────────────────────────┘
```

### Receiving Detail with Print Button

```
┌──────────────────────────────────────────────────────┐
│  Shopmate Supplies                            ← Back  │
├──────────────────────────────────────────────────────┤
│                                                      │
│  ✓ 5 / 12 boxes received                            │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │ Scan carton ID                                 │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  [  📦 Receive  ] [  ✗ Not Received  ] [Damaged]    │
│                                                      │
│  ──────────────────────────────────────────────────  │
│                                                      │
│  □ BOX-1001                      ✓ RECEIVED         │
│    [Print Label]                                     │
│                                                      │
│  □ BOX-1002                      ✓ RECEIVED         │
│    [Print Label]                                     │
│                                                      │
│  □ BOX-1003                      ⊗ NOT RECEIVED     │
│                                                      │
│  □ BOX-1004                      ⊗ DAMAGED          │
│                                                      │
│  □ BOX-1005                      ⏳ EXPECTED        │
│                                                      │
│                              [        Done       ]   │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### Unpacking Detail with Batch Print Button

```
┌──────────────────────────────────────────────────────┐
│  Kitchen Supplies                             ← Back  │
├──────────────────────────────────────────────────────┤
│                                                      │
│  BOX-2301                        25 / 25 counted    │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │ Scan product code                              │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  [  Resolve  ] [  💡 Suggest  ]  [  Finish  ]       │
│                                                      │
│  ──────────────────────────────────────────────────  │
│                                                      │
│  □ Face Cream 50ml (PERSONAL_CARE)         25 ✓     │
│    Cost: ₹120 | MRP: ₹299                          │
│                                                      │
│                              [Print 25 Labels]      │
│                                                      │
│  ──────────────────────────────────────────────────  │
│                                                      │
│  [    Close Batch    ]                              │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### Label Mockup (4x6 thermal label output)

```
╔════════════════════════════════════╗
║                                    ║
║  █████████████████████████████     ║
║  █ ███ █ █ █ █ █████ █ █ █ █ █    ║  ← Code128 barcode
║  █ █   █   █ █ █     █   █   █    ║
║  █ ███ █ █ █ █ █████ █ █ █ █ █    ║
║  C0D3-2024-5871                    ║
║                                    ║
║  Coconut Oil 1L                    ║
║  Kitchen Essentials                ║
║                                    ║
║  Cost: ₹240/unit    MRP: ₹450      ║  Lot: LOT-2024-07
║  Received: 2026-07-27              ║
║                                    ║
╚════════════════════════════════════╝
```

## Acceptance Criteria

✅ Printer config screen reachable from admin nav
✅ Test print executes and updates status badge
✅ Config saved to DB and persists across restarts
✅ Print button visible in Receiving detail, Unpacking detail, Catalog
✅ Clicking print queues a job (response shows jobId immediately)
✅ Queued job executes within 2 seconds (printer available) or retries 10 times (printer down) then fails
✅ Label renders with correct barcode, product name, category, cost, MRP
✅ Batch print (qty > 1) produces qty labels without timeout
✅ Offline queue persists: if printer goes down, queued jobs wait; when printer back up, jobs resume
✅ Error handling: print failure shows user-friendly message (not stack trace)
✅ No blocking: print requests don't block goods-in or unpacking workflows
