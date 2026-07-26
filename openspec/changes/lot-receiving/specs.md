# Specifications: Lot Receiving Workflow

## 1. Box Receipt Tracking

### Requirement 1.1: Box State Initialization
When a lot is imported via manifest, each manifest carton creates a `box_receipt` record in `EXPECTED` state.

**Scenario 1.1.1:** Import manifest with 3 cartons
```
Given: Manifest for LOT-0231 with cartons [BOX-0231-001, BOX-0231-002, BOX-0231-003]
When: Manifest imported via ConsignmentImporter
Then: 3 box_receipt rows created with state = EXPECTED
  And: Lot marked OPEN, receiving_complete = false
```

### Requirement 1.2: Receive Box
Operator scans a carton ID. If it matches manifest for the lot, mark box RECEIVED with timestamp.

**Scenario 1.2.1:** Valid carton scan
```
Given: LOT-0231 in state OPEN, BOX-0231-001 in state EXPECTED
When: Operator scans BOX-0231-001
Then: box_receipt.state = RECEIVED
  And: box_receipt.received_at = now
  And: API returns { receivedCount: 1, totalExpected: 3, receivingComplete: false }
```

**Scenario 1.2.2:** Carton not in manifest
```
Given: LOT-0231 expects [BOX-0231-001, ..., BOX-0231-003]
When: Operator scans BOX-9999-999 (not in manifest)
Then: API returns error { code: "BOX_NOT_IN_MANIFEST", message: "..." }
  And: No box_receipt created
```

**Scenario 1.2.3:** Carton already scanned
```
Given: BOX-0231-001 in state RECEIVED
When: Operator scans BOX-0231-001 again
Then: API returns error { code: "BOX_ALREADY_RECEIVED", message: "..." }
  And: state unchanged
```

### Requirement 1.3: Mark Receiving Complete
When all manifest cartons for a lot have been scanned RECEIVED, lot moves to `FULLY_RECEIVED`.

**Scenario 1.3.1:** All cartons received
```
Given: LOT-0231 expects 3 boxes, all 3 scanned RECEIVED
When: Operator triggers receiving close (or auto-detect)
Then: Lot.receiving_complete = true
  And: Lot state (implicit) = FULLY_RECEIVED
  And: UI switches to "Ready to unpack" message
```

**Scenario 1.3.2:** Not all cartons received, lot closed anyway
```
Given: LOT-0231 expects 47 boxes, only 32 received
When: Operator selects "Done" on receiving screen
Then: Lot.receiving_complete remains false
  And: Unpack screen remains gated (offer message: "Receiving incomplete. Mark missing boxes?")
```

---

## 2. Box Rejection

### Requirement 2.1: Not Received
Operator marks a carton as not arrived (during receiving).

**Scenario 2.1.1:** Mark box NOT_RECEIVED
```
Given: LOT-0231, BOX-0231-042 in state EXPECTED
When: Operator clicks "Not here?" and confirms
Then: box_receipt.state = NOT_RECEIVED
  And: box_receipt.received_at remains NULL
  And: Lot.receiving_complete logic: count NOT_RECEIVED as "accounted for" (not blocking close)
  And: UI updates: "⚠ BOX-0231-042 marked not received"
```

### Requirement 2.2: Rejected (Receiving)
Operator marks carton REJECTED at receiving if obvious damage visible.

**Scenario 2.2.1:** Mark box REJECTED during receiving
```
Given: LOT-0231, BOX-0231-001 in state RECEIVED (scanned at dock)
When: Operator clicks "Damaged?" and notes "Water damage, unopened"
Then: box_receipt.state = REJECTED
  And: box_receipt.rejected_reason = "Water damage, unopened"
  And: API response includes rejection reason
```

### Requirement 2.3: Rejected (Unpacking)
Operator marks carton REJECTED if damage discovered while unpacking.

**Scenario 2.3.1:** Mark box REJECTED mid-unpacking
```
Given: LOT-0231, BOX-0231-001 in state UNPACKING
  And: 5 items already scanned from this box
When: Operator clicks "Reject box" and notes "Items inside broken"
Then: box_receipt.state = REJECTED
  And: box_receipt.rejected_reason = "Items inside broken"
  And: Items already scanned in this box are NOT rolled back (recorded in stock_movement with DAMAGED flag)
  And: box state transition is UNPACKING → REJECTED
```

---

## 3. Unpacking Flow

### Requirement 3.1: Select Box
Operator selects which box to open for unpacking (gated: must be in RECEIVED or UNPACKING state).

**Scenario 3.1.1:** Box list filtered to RECEIVED/UNPACKING
```
Given: LOT-0231 with boxes:
  - BOX-0231-001 (RECEIVED)
  - BOX-0231-002 (UNPACKED) ✗ skip
  - BOX-0231-003 (REJECTED) ✗ skip
  - BOX-0231-004 (EXPECTED) ✗ skip (not received yet)
When: Operator opens box selector
Then: Dropdown shows only BOX-0231-001 and BOX-0231-003
  And: BOX-0231-001 is offered first
```

**Scenario 3.1.2:** No unpackable boxes
```
Given: LOT-0231 with all boxes either UNPACKED, REJECTED, or NOT_RECEIVED
When: Operator tries to unpack
Then: Message: "All boxes processed. Ready to close lot?"
  And: Close button offered
```

### Requirement 3.2: Scan Item
Operator scans product (barcode or internal code) and quantity for a box.

**Scenario 3.2.1:** Item in manifest
```
Given: BOX-0231-001 open, manifest expects [PRODUCT-A × 5, PRODUCT-B × 3]
When: Operator scans PRODUCT-A, qty auto-filled to 5
Then: stock_movement recorded with:
  - Quantity: 5
  - Product: PRODUCT-A
  - Batch: box batch (manifest batch for this carton/product line)
  - Movement type: RECEIVED
  And: Count display: "5/5 for this item"
  And: Input clears, ready for next scan
```

**Scenario 3.2.2:** Qty scanned != manifest
```
Given: Manifest expects PRODUCT-A × 5 in BOX-0231-001
When: Operator enters qty 4
Then: ⚠ Warning: "Expected 5, got 4. Difference: -1"
  And: Still allows record (assumption: minor variance, damage, or manifest error)
  And: Difference flagged for remediation later
```

**Scenario 3.2.3:** Product not in manifest for this box
```
Given: BOX-0231-001 manifest: [PRODUCT-A, PRODUCT-B]
When: Operator scans PRODUCT-C (unknown product in this box)
Then: ⚠ Alert: "PRODUCT-C not expected in this box. Add anyway?"
  And: If confirmed: recorded, flagged as UNEXPECTED_IN_BOX for audit
```

### Requirement 3.3: Capture MRP
If product MRP not known, prompt operator.

**Scenario 3.3.1:** Product seen before (has MRP)
```
Given: PRODUCT-A has MRP = ₹500
When: Operator scans PRODUCT-A
Then: MRP pre-filled, no prompt
```

**Scenario 3.3.2:** Product new (no MRP)
```
Given: PRODUCT-X has no MRP in system
When: Operator scans PRODUCT-X
Then: Prompt: "MRP for PRODUCT-X?"
  And: Input field appears
  And: Operator enters ₹299
  And: Product.mrp = 299 (or recorded in observation if MRP may vary by delivery)
```

### Requirement 3.4: Box Complete
When all items in box are scanned, operator moves to next box or closes unpacking.

**Scenario 3.4.1:** Not all items scanned yet
```
Given: BOX-0231-001 manifest: PRODUCT-A × 5, PRODUCT-B × 3 (total 2 lines)
  And: Only PRODUCT-A scanned (5 items)
When: Operator clicks "Next box" without scanning PRODUCT-B
Then: ⚠ Warning: "1 item type not scanned (PRODUCT-B × 3). Continue anyway?"
  And: If yes: box marked UNPACKED_INCOMPLETE, move to next box
  And: If no: stay on current box
```

**Scenario 3.4.2:** All items in box scanned
```
Given: BOX-0231-001 all items scanned (PRODUCT-A 5/5, PRODUCT-B 3/3)
When: Operator clicks "Next box"
Then: box_receipt.state = UNPACKED
  And: Move to BOX-0231-002 (or close if last box)
```

---

## 4. Lot Closure

### Requirement 4.1: Lot Ready to Close
Lot can close only when all boxes are terminal (UNPACKED, NOT_RECEIVED, REJECTED).

**Scenario 4.1.1:** Lot ready
```
Given: LOT-0231 boxes:
  - BOX-0231-001 (UNPACKED)
  - BOX-0231-002 (UNPACKED)
  - BOX-0231-003 (REJECTED)
When: Operator clicks "Close Lot"
Then: Validation passes (all boxes terminal)
  And: Proceed to cost allocation
```

**Scenario 4.1.2:** Lot not ready
```
Given: LOT-0231 boxes:
  - BOX-0231-001 (UNPACKED)
  - BOX-0231-042 (EXPECTED, never received, never marked NOT_RECEIVED)
When: Operator clicks "Close Lot"
Then: Error: "Cannot close. Box BOX-0231-042 status unknown. Mark as not received?"
  And: Lot remains open
```

### Requirement 4.2: Cost Allocation
On lot close, allocate total cost across boxes, excluding rejected/not-received.

**Scenario 4.2.1:** Full close
```
Given: LOT-0231, total cost ₹945,662.95
  And: All 47 boxes UNPACKED
When: Close lot
Then: Cost pool = ₹945,662.95
  And: Allocate to items in all boxes by manifest-based weighting
  And: Each item gets cost per unit: cost / qty
```

**Scenario 4.2.2:** Close with rejections
```
Given: LOT-0231, total cost ₹945,662.95
  And: 45 boxes UNPACKED, 1 REJECTED (manifest value ₹30k), 1 NOT_RECEIVED (manifest value ₹20k)
When: Close lot
Then: Cost pool = ₹945,662.95 - ₹30k - ₹20k = ₹895,662.95
  And: Allocate only to 45 UNPACKED boxes' items
  And: Rejected/not-received boxes contribute zero items, zero cost
```

### Requirement 4.3: Inventory Becomes Sellable
After cost allocation, products move to SELLABLE state (can be priced).

**Scenario 4.3.1:** Products available for sale
```
Given: Lot closed, costs allocated
When: Check product pricing screen
Then: Products show:
  - Cost per unit (from allocation)
  - Suggested price (cost × margin %)
  And: Operator can set actual selling price
```

---

## 5. UI States

### Screen: Receiving
- **Header:** Lot name, "32/47 boxes received"
- **Input:** Carton ID scan field (large, autofocus)
- **Actions:**
  - "Not here?" → mark NOT_RECEIVED
  - "Damaged?" → mark REJECTED with notes
  - "Done" → return to menu (OK even if incomplete)
- **Feedback:** "✓ BOX-0231-001 received" (green flash)

### Screen: Unpack
- **Header:** Lot name, box selector
- **Inputs:**
  - Box dropdown (pre-filled if just received)
  - Item scan field (large)
  - MRP (if needed)
- **Actions:**
  - "Reject box" → mark box REJECTED
  - "Next box" → validate, move next
  - "Done" → return to menu
- **Progress:** "3/5 items scanned in this box"

---

## 6. API Contracts

### POST /api/lots/{lotId}/receive-box
**Request:**
```json
{ "manifestCartonId": "BOX-0231-001" }
```
**Response 200:**
```json
{
  "boxId": "uuid-xxx",
  "state": "RECEIVED",
  "receivedAt": "2026-07-26T10:30:00Z",
  "lot": {
    "id": "lot-0231",
    "receivedCount": 32,
    "totalExpected": 47,
    "receivingComplete": false
  }
}
```
**Response 400:**
```json
{ "code": "BOX_NOT_IN_MANIFEST", "message": "..." }
```

### POST /api/lots/{lotId}/reject-box
**Request:**
```json
{
  "manifestCartonId": "BOX-0231-001",
  "reason": "NOT_RECEIVED" | "REJECTED",
  "notes": "Water damage"
}
```
**Response 200:**
```json
{ "state": "NOT_RECEIVED" | "REJECTED", "rejectedReason": "Water damage" }
```

### POST /api/lots/{lotId}/mark-box-unpacked
**Request:**
```json
{ "manifestCartonId": "BOX-0231-001" }
```
**Response 200:**
```json
{
  "state": "UNPACKED",
  "lotReadyToClose": false,
  "nextBox": { "manifestCartonId": "BOX-0231-002", "state": "RECEIVED" }
}
```

### POST /api/lots/{lotId}/close
(Existing; gating added: verify all boxes terminal before proceeding.)

---

## 7. Edge Cases

**E1:** Lot received, unpacking started, then receiving incomplete notice comes in (late box arrives)
- Action: Operator scans new box during receiving
- Result: Lot can still close (new box RECEIVED, then UNPACKED, then lot close)

**E2:** Operator scans same item twice in one box
- Action: Qty increments
- Result: total exceeds manifest, warning shown

**E3:** Lot partially unpacked, operator closes without unpacking all boxes
- Action: Click "Close lot" with some boxes still RECEIVED
- Result: Error; must mark remaining boxes rejected or unpacked first
