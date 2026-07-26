# Tasks: Lot Receiving Workflow

## Section 1: Database & Entities (8 tasks)

### 1.1: Add box_receipt table (Flyway migration)
- [ ] Create `V6__add_box_receipt_table.sql`
- Table: `box_receipt` with columns: id (UUID PK), lot_id (FK), manifest_carton_id, box_state, received_at, rejected_reason, created_at, updated_at
- Add index on (lot_id, manifest_carton_id) for lookup speed
- Add index on box_state for filtering

### 1.2: Add receiving_complete column to lot table
- [ ] Create `V7__add_lot_receiving_complete.sql`
- Add `receiving_complete BOOLEAN DEFAULT FALSE` to lot table
- Set to true when all manifest boxes for lot are RECEIVED, NOT_RECEIVED, or REJECTED

### 1.3: Create BoxReceipt entity (Spring Data JPA)
- [ ] File: `backend/src/main/java/net/bahi/khaata/goods/domain/BoxReceipt.java`
- Fields: id, lotId, manifestCartonId, boxState (enum), receivedAt, rejectedReason, createdAt, updatedAt
- Use `@Enumerated(EnumType.STRING)` for boxState
- Add `@EntityListeners(AuditingEntityListener.class)` for timestamps

### 1.4: Create BoxState enum
- [ ] File: `backend/src/main/java/net/bahi/khaata/goods/domain/BoxState.java`
- Values: EXPECTED, RECEIVED, UNPACKING, UNPACKED, NOT_RECEIVED, REJECTED
- Implement terminal state check: `isTerminal()` returns true for UNPACKED, NOT_RECEIVED, REJECTED

### 1.5: Create BoxReceiptRepository (Spring Data)
- [ ] File: `backend/src/main/java/net/bahi/khaata/goods/persistence/BoxReceiptRepository.java`
- Methods:
  - `findByLotIdAndManifestCartonId(lotId, cartonId): Optional<BoxReceipt>`
  - `findByLotId(lotId): List<BoxReceipt>`
  - `countByLotIdAndBoxState(lotId, RECEIVED|UNPACKING|UNPACKED): long`
  - `countByLotIdAndBoxStateIn(lotId, terminal states): long`

### 1.6: Update Lot entity for receiving_complete
- [ ] Modify `backend/src/main/java/net/bahi/khaata/goods/domain/Lot.java`
- Add field: `receivingComplete: Boolean`
- Add method: `isReceivingComplete()` checks all boxes are terminal

### 1.7: Add migration data for existing lots
- [ ] Create `V8__backfill_box_receipt_for_existing_lots.sql`
- For each lot in CLOSED state, create box_receipt records (EXPECTED→UNPACKED for all manifest cartons)
- For LOT-0231 (test lot): simulate historical receiving

### 1.8: Add database schema validation test
- [ ] File: `backend/src/test/java/net/bahi/khaata/goods/BoxReceiptSchemaTest.java`
- Verify box_receipt table exists, columns correct, indexes present
- Run Flyway migration, assert no errors

---

## Section 2: Backend API Endpoints (6 tasks)

### 2.1: POST /api/lots/{lotId}/receive-box endpoint
- [ ] File: `backend/src/main/java/net/bahi/khaata/goods/api/ReceivingController.java`
- Handler: `receiveBox(lotId, ReceiveBoxRequest)`
  - Validate lot exists and state is OPEN
  - Look up box_receipt by (lotId, manifestCartonId)
  - If EXPECTED: transition to RECEIVED, set received_at = now
  - If already RECEIVED: error BOX_ALREADY_RECEIVED
  - If not in manifest: error BOX_NOT_IN_MANIFEST
- Return: { boxId, state, receivedAt, lot { receivedCount, totalExpected, receivingComplete } }

### 2.2: POST /api/lots/{lotId}/reject-box endpoint
- [ ] File: `backend/src/main/java/net/bahi/khaata/goods/api/ReceivingController.java`
- Handler: `rejectBox(lotId, RejectBoxRequest)`
  - Validate lot exists
  - Look up box_receipt
  - Transition: EXPECTED/RECEIVED → NOT_RECEIVED or REJECTED
  - Store rejection reason
  - If UNPACKING → REJECTED: flag items scanned in this box with DAMAGE_FLAG or similar
- Return: { state, rejectedReason }

### 2.3: GET /api/lots/{lotId}/boxes endpoint
- [ ] File: `backend/src/main/java/net/bahi/khaata/goods/api/ReceivingController.java`
- Handler: `getBoxesForLot(lotId)`
- Return array of:
  ```
  {
    manifestCartonId,
    state,
    receivedAt,
    rejectedReason (if REJECTED),
    itemsUnpackedCount,
    itemsExpectedCount
  }
  ```
- Include summary: { receivingComplete, allTerminal, counts { expected, received, unpacked, rejected, notReceived } }

### 2.4: POST /api/lots/{lotId}/mark-box-unpacked endpoint
- [ ] File: `backend/src/main/java/net/bahi/khaata/goods/api/UnpackingController.java`
- Handler: `markBoxUnpacked(lotId, MarkBoxUnpackedRequest)`
  - Validate lot exists
  - Look up box_receipt
  - Transition: UNPACKING → UNPACKED
  - Query stock_movement for this box: sum quantities, verify matches manifest lines
  - If mismatch: allow (variance flagged), still mark UNPACKED
  - Return: { state: UNPACKED, lotReadyToClose, nextBox }

### 2.5: PUT /api/lots/{lotId}/boxes/{boxId}/state endpoint
- [ ] File: `backend/src/main/java/net/bahi/khaata/goods/api/ReceivingController.java`
- Generic state transition handler (for testing + edge cases)
- Allow: EXPECTED→RECEIVED, RECEIVED→UNPACKING, UNPACKING→UNPACKED, etc.
- Validate state machine (no invalid transitions)

### 2.6: Add ReceivingService (business logic)
- [ ] File: `backend/src/main/java/net/bahi/khaata/goods/service/ReceivingService.java`
- Methods:
  - `receiveBox(lotId, cartonId): BoxReceipt`
  - `rejectBox(lotId, cartonId, reason, notes): BoxReceipt`
  - `markBoxUnpacked(lotId, cartonId): BoxReceipt`
  - `isReceivingComplete(lotId): Boolean` (all boxes terminal)
  - `validateLotCanClose(lotId)` (gating for cost allocation)

---

## Section 3: Cost Allocation Integration (3 tasks)

### 3.1: Update LotCloseService to gate on receiving complete
- [ ] Modify `backend/src/main/java/net/bahi/khaata/goods/service/LotCloseService.java`
- Before cost allocation, call `receivingService.validateLotCanClose(lotId)`
- If any box not terminal: throw `IncompleteReceivingException`
- Error message: "Cannot close lot. Boxes pending: [list]"

### 3.2: Exclude rejected/not-received boxes from cost pool
- [ ] Modify cost allocation logic in `ConsignmentImporter` or `LotCostAllocator`
- Query box_receipt for all UNPACKED boxes only
- Subtract manifest value of REJECTED/NOT_RECEIVED boxes from total cost
- Allocate remaining cost only across UNPACKED box items
- Example: total cost ₹945k, reject ₹50k → allocate ₹895k

### 3.3: Add cost allocation tests (rejection scenario)
- [ ] File: `backend/src/test/java/net/bahi/khaata/goods/LotCloseWithRejectionTest.java`
- Test 1: Import 47-box lot, reject 1, unpacking close → cost redistributed
- Test 2: Import 47-box lot, mark 2 NOT_RECEIVED, close → cost reduced
- Test 3: Verify no division-by-zero when all boxes rejected (edge case)

---

## Section 4: Frontend: Receiving Screen (5 tasks)

### 4.1: Create ReceivingView JavaFX component
- [ ] File: `terminal/src/main/java/net/bahi/khaata/terminal/view/ReceivingView.java`
- Screens:
  - Menu (buttons: Receive, Unpack)
  - Receive (lot selector, carton scan input, progress, actions)
- Use VBox/HBox layout, large font for scan input
- Reuse Till's existing Lot selector dropdown

### 4.2: Implement carton scan logic
- [ ] File: `terminal/src/main/java/net/bahi/khaata/terminal/controller/ReceivingViewController.java`
- On Enter in scan field:
  - Extract carton ID
  - POST /api/lots/{id}/receive-box
  - Show feedback: "✓ BOX-XXX received (32/47)" or error
  - Clear input, re-focus
  - Update progress bar

### 4.3: Add "Not here?" modal
- [ ] File: `terminal/src/main/java/net/bahi/khaata/terminal/view/RejectBoxDialog.java`
- Dialog: "Mark box as not received?"
- Input: Notes (optional)
- Button: Confirm / Cancel
- On confirm: POST /api/lots/{id}/reject-box with reason=NOT_RECEIVED

### 4.4: Add "Damaged?" modal
- [ ] File: `terminal/src/main/java/net/bahi/khaata/terminal/view/RejectBoxDialog.java` (same file, reuse)
- Dialog: "Mark box as rejected?"
- Input: Damage notes (e.g., "Water damage, unopened")
- Button: Confirm / Cancel
- On confirm: POST /api/lots/{id}/reject-box with reason=REJECTED

### 4.5: Add receiving progress display
- [ ] Display in ReceivingView: "32 of 47 boxes received" (large number)
- Add progress bar (if 47/47: change message to "✓ All boxes received. Ready to unpack.")
- Add "Done" button (returns to menu regardless of completion state)

---

## Section 5: Frontend: Unpacking Screen (6 tasks)

### 5.1: Create UnpackingView JavaFX component
- [ ] File: `terminal/src/main/java/net/bahi/khaata/terminal/view/UnpackingView.java`
- Screen layout:
  - Lot selector (dropdown)
  - Box selector (dropdown, filtered to RECEIVED/UNPACKING)
  - Item scan input (large)
  - Item feedback (qty/expected)
  - Actions: Reject box, Next box, Done

### 5.2: Implement box selector
- [ ] On ReceivingView → select Unpack, fetch lot
- GET /api/lots/{id}/boxes
- Populate dropdown with boxes in RECEIVED or UNPACKING state
- Mark next unpackable box as default

### 5.3: Implement item scan + stock movement
- [ ] On Enter in item scan field:
  - POST /api/stock-movements/add or similar (existing endpoint)
  - Include: lotId, boxId (derived from box_receipt), productId, qty, mrp
  - Show feedback: "✓ PRODUCT-A added (qty: 5)"
  - If qty != manifest: show warning "Expected 5, got 4"
  - Clear input, re-focus

### 5.4: Add MRP capture modal
- [ ] File: `terminal/src/main/java/net/bahi/khaata/terminal/view/MrpInputDialog.java`
- Trigger when: product scanned with no known MRP
- Input: MRP value
- Button: Enter / Cancel
- On enter: record MRP, continue with stock movement

### 5.5: Add "Reject box" action
- [ ] In UnpackingView, button: "Reject box"
- Calls: POST /api/lots/{id}/reject-box with reason=REJECTED, notes
- Feedback: "✓ Box rejected. Items already scanned remain recorded."
- Move to next box

### 5.6: Implement box completion + next box
- [ ] Track items scanned vs. manifest for current box
- On "Next box" button:
  - If not all items scanned: warning "1 item type not scanned. Continue anyway?"
  - If yes: POST /api/lots/{id}/mark-box-unpacked
  - Move to next box or show "All boxes unpacked" message

---

## Section 6: Integration & Testing (5 tasks)

### 6.1: End-to-end receiving test
- [ ] File: `backend/src/test/java/net/bahi/khaata/goods/ReceivingE2ETest.java`
- Import manifest (LOT-0231, 47 boxes)
- Scan all 47 boxes via API
- Verify lot.receiving_complete = true
- Verify all box_receipt.state = RECEIVED
- Verify unpack endpoint unlocked (no error)

### 6.2: End-to-end unpacking test
- [ ] File: `backend/src/test/java/net/bahi/khaata/goods/UnpackingE2ETest.java`
- Prerequisite: lot with boxes all RECEIVED
- Scan items in first box
- POST mark-box-unpacked
- Verify box_receipt.state = UNPACKED
- Move to next box, repeat
- Close lot → verify costs allocated

### 6.3: End-to-end rejection test
- [ ] File: `backend/src/test/java/net/bahi/khaata/goods/RejectionE2ETest.java`
- Import manifest (10 boxes, ₹100k total)
- Receive 8 boxes
- Reject 2 boxes (NOT_RECEIVED, REJECTED)
- Close lot → verify cost pool = ₹? (reduced by manifest value of 2 boxes)
- Verify items in 8 boxes got allocated costs

### 6.4: Terminal UI receiving test (manual / probe)
- [ ] File: `terminal/src/test/java/net/bahi/khaata/terminal/ReceivingViewProbeTest.java`
- Boot real terminal + backend
- Scan carton IDs
- Verify UI updates progress correctly
- Verify reject modals work
- Verify feedback messages shown

### 6.5: Terminal UI unpacking test (manual / probe)
- [ ] File: `terminal/src/test/java/net/bahi/khaata/terminal/UnpackingViewProbeTest.java`
- Boot real terminal + backend
- Select lot + box
- Scan items
- MRP prompts appear + work
- Next box button transitions correctly
- Close lot button appears when all unpacked

---

## Section 7: Documentation & Deployment (3 tasks)

### 7.1: Update API documentation
- [ ] Add OpenAPI spec / Swagger annotations for new endpoints:
  - POST /api/lots/{lotId}/receive-box
  - POST /api/lots/{lotId}/reject-box
  - GET /api/lots/{lotId}/boxes
  - POST /api/lots/{lotId}/mark-box-unpacked

### 7.2: Add receiving/unpacking user guide
- [ ] File: `docs/warehouse-flow.md`
- Screenshots of ReceivingView + UnpackingView
- Step-by-step: import → receive → unpack → close

### 7.3: Migration checklist for WAL provisioning
- [ ] Note in deployment: V8 backfill migration will create box_receipt rows for all existing lots
- Verify no locks during migration (SQLite single-threaded)
- Test on copy of production DB first

---

## Summary

**Total: 40 tasks**
- Section 1 (DB): 8 tasks
- Section 2 (API): 6 tasks
- Section 3 (Cost allocation): 3 tasks
- Section 4 (Receiving UI): 5 tasks
- Section 5 (Unpacking UI): 6 tasks
- Section 6 (Testing): 5 tasks
- Section 7 (Docs): 3 tasks

**Estimated effort:** 3–4 weeks (depends on parallelization and pre-existing patterns)
