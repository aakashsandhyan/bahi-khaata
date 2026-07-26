# Tasks: Manual Lot Management

## 1. Backend: Schema and Entity

### 1.1 Add is_manual column to lot table
- File: backend/src/main/resources/db/migration/V31_*.sql
- Action: `ALTER TABLE lot ADD COLUMN is_manual BOOLEAN NOT NULL DEFAULT false;`
- Verify: Schema migrates cleanly, existing lots have is_manual=false

### 1.2 Update Lot entity with isManual field
- File: backend/src/main/java/com/bahikhaata/backend/inventory/Lot.java
- Action: Add `private boolean isManual;` field, getter, updated constructor
- Verify: Entity compiles, Hibernate handles boolean → BOOLEAN mapping

### 1.3 Update LotRepository (no changes needed)
- Verify: Existing queries still work (no isManual-specific queries yet)

## 2. Backend: API Endpoints

### 2.1 Create POST /api/lots/manual endpoint
- File: backend/src/main/java/com/bahikhaata/backend/inventory/LotController.java
- Action: Add method createManualLot(@RequestBody CreateManualLotRequest request)
- Creates: Lot with isManual=true, freight=0, no BoxReceipts
- Returns: LotSummaryDto with isManual=true, expected=0
- Validation: supplier not blank, amount > 0, date valid, allocationMethod valid
- Tests: LotsControllerTest (existing) add tests for manual lot creation

### 2.2 Create POST /api/lots/{lotId}/add-product endpoint
- File: backend/src/main/java/com/bahikhaata/backend/inventory/LotController.java (or new ManualLotsController.java)
- Action: Add method addProductToLot(@PathVariable UUID lotId, @RequestBody AddProductRequest request)
- Precondition: Lot.isManual=true (validate, 400 if false)
- Creates: ExpectedLine with code/name/quantity/categoryCode
- Returns: {success, totalProducts, totalQuantity, allocationPerUnit}
- Validation: name not blank, quantity > 0, categoryCode valid
- Tests: Add to LotsControllerTest

### 2.3 Update GET /api/lots endpoint to include isManual
- File: backend/src/main/java/com/bahikhaata/backend/inventory/LotController.java
- Action: Update LotSummaryDto to add isManual field
- Verify: Existing tests pass, manual lots show isManual=true

## 3. Backend: Request/Response DTOs

### 3.1 Create CreateManualLotRequest record
- File: backend/src/main/java/com/bahikhaata/contracts/CreateManualLotRequest.java
- Fields: supplier (String), receivedOn (String), amountPaidPaise (long), allocationMethod (AllocationMethod)
- Validation: in constructor or via @Valid

### 3.2 Create AddProductRequest record
- File: backend/src/main/java/com/bahikhaata/contracts/AddProductRequest.java
- Fields: code (String, nullable), name (String), quantity (long), categoryCode (String), estimatedCostPaise (Long, nullable)
- Validation: in constructor

### 3.3 Update LotSummaryDto to include isManual
- File: backend/src/main/java/com/bahikhaata/backend/inventory/LotController.java (record)
- Add field: boolean isManual

## 4. Frontend: Types and API

### 4.1 Update types.ts
- File: dashboard/web/src/types.ts
- Action: Update LotSummary interface to add isManual: boolean
- Add: CreateManualLotRequest, AddProductRequest, AddProductResponse interfaces

### 4.2 Update api.ts
- File: dashboard/web/src/api.ts
- Action: Extend lots export with:
  - `createManual(supplier, receivedOn, amountPaidPaise): Promise<LotSummary>`
  - `addProduct(lotId, code, name, quantity, categoryCode, estimatedCostPaise): Promise<AddProductResponse>`

## 5. Frontend: Lot Management Screen

### 5.1 Create LotManagement.tsx component
- File: dashboard/web/src/LotManagement.tsx
- Structure: Overview with tabs (In Progress / Complete)
- Tabs: Filter lots by receivingComplete (same pattern as Receiving)
- List: Show all open lots with lot type indicator
- Each lot card: supplier, date, amount, type badge (manual vs manifest), progress (if manifest lot)
- "Create Lot" button → modal

### 5.2 Create lot creation modal
- File: LotManagement.tsx (component)
- Form: supplier (text), date (date picker), amount (number), type (dropdown: Manifest / Manual)
- When type="Manual": allocationMethod hidden, defaults to RELATIVE_MRP
- Submit: POST /api/lots/manual → close modal, refresh list
- Error handling: show validation errors in modal
- Cancel: close modal

### 5.3 Wire LotManagement into App.tsx
- File: dashboard/web/src/App.tsx
- Action: Add route /lots → LotManagement component
- Add navbar link: "Lot Management"

### 5.4 Update styles.css if needed
- File: dashboard/web/src/styles.css
- Action: Add styles for lot-management screen (reuse .receiving class or create new)
- Add badge styles for manual/manifest indicator

## 6. Frontend: Receiving Update

### 6.1 Update Receiving.tsx for manual product entry
- File: dashboard/web/src/Receiving.tsx
- Modification: Detail view checks lot.isManual
- If isManual=true: show product-entry form (not box scanner)
- Form fields: code (optional), name, category, qty, estimated cost (optional)
- "Add Product" button: POST to /api/lots/{id}/add-product
- Product list: accumulate entries with running totals
- "Done" button: enabled when ≥1 product, closes lot

### 6.2 Update Receiving.tsx to show lot type in overview
- File: dashboard/web/src/Receiving.tsx
- Modification: Add "(M)" or "manual" badge to manual lots in overview

## 7. Testing

### 7.1 Backend unit/integration tests
- Test file: backend/src/test/java/com/bahikhaata/backend/inventory/LotsControllerTest.java
- Tests: create manual lot, add products, validation, manual lot in list, manifest lots unaffected

### 7.2 Frontend component tests (optional, manual testing OK)
- Verify: LotManagement screen loads, create button opens modal, modal validation works, lot appears in Receiving
- Verify: Receiving detail shows product form for manual lots, product accumulates, Done button works

### 7.3 End-to-end test
- Create manual lot via LotManagement
- Go to Receiving, open manual lot
- Add 2-3 products via form
- Click Done, lot closes
- Lot appears in Receiving complete tab

## 8. Cleanup and Finish

### 8.1 Build and test
- Backend: `./gradlew :backend:test`
- Frontend: `npm run build`
- Dashboard dev: `npm run dev` (manual test end-to-end workflow)

### 8.2 Commit
- Message: "feat: add manual lot management (create lots, add products manually)"
- Include: schema migration, backend endpoints, frontend screens

### 8.3 Update OpenSpec
- Mark change complete
- Sync to main specs (archive command)
