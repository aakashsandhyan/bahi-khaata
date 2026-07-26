# Design: Lot Receiving Workflow

## Core Model: Box State Machine

Each carton/box in a lot has a lifecycle:

```
EXPECTED (manifest imported, not yet arrived)
    ↓
RECEIVED (carton ID scanned at dock)
    ├→ NOT_RECEIVED (marked during receiving: never arrived)
    ├→ REJECTED (marked during receiving: obvious damage, unopened)
    └→ UNPACKING (operator opens, starts scanning items)
         ├→ UNPACKED (complete: all items counted + MRP)
         └→ REJECTED (marked during unpacking: functional damage, mid-scan)
```

Terminal states: `UNPACKED`, `NOT_RECEIVED`, `REJECTED`

## Lot Closure Gate

Lot moves to `CLOSED` (and costs allocate) only when:
- **All boxes in lot reach a terminal state** (UNPACKED OR NOT_RECEIVED OR REJECTED)
- No boxes remain in EXPECTED, RECEIVED, or UNPACKING

## Schema Changes

### New Table: `box_receipt`
Track physical receipt of each manifest carton.

```sql
CREATE TABLE box_receipt (
  id CHAR(36) PRIMARY KEY,
  lot_id CHAR(36) NOT NULL,
  manifest_carton_id VARCHAR(50) NOT NULL,
  box_state VARCHAR(20) NOT NULL DEFAULT 'EXPECTED',
  received_at TIMESTAMP NULL,
  rejected_reason VARCHAR(255) NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (lot_id) REFERENCES lot(id)
);
```

### Modify: `lot` table
Add field to track if lot is gated:

```sql
ALTER TABLE lot ADD COLUMN receiving_complete BOOLEAN DEFAULT FALSE;
```

(Or: derive it from `box_receipt` state counts. Simpler: explicit column.)

### Modify: `stock_movement`
When cost allocation happens (lot close), verify all boxes terminal. If any non-terminal, reject close.

## API Endpoints

### POST /api/lots/{lotId}/receive-box
Mark a carton as received.

**Request:**
```json
{
  "manifestCartonId": "BOX-0231-001"
}
```

**Response:**
```json
{
  "boxId": "uuid",
  "state": "RECEIVED",
  "receivedAt": "2026-07-26T...",
  "lot": {
    "id": "...",
    "receivedCount": 32,
    "totalExpected": 47,
    "receivingComplete": false
  }
}
```

### POST /api/lots/{lotId}/reject-box
Mark a carton as not received or rejected.

**Request:**
```json
{
  "manifestCartonId": "BOX-0231-001",
  "reason": "NOT_RECEIVED" | "REJECTED",
  "notes": "..."
}
```

### GET /api/lots/{lotId}/boxes
List all boxes for lot with current state.

**Response:**
```json
{
  "boxes": [
    {
      "manifestCartonId": "BOX-0231-001",
      "state": "RECEIVED",
      "receivedAt": "2026-07-26T10:30Z",
      "itemsUnpacked": 12
    },
    {
      "manifestCartonId": "BOX-0231-002",
      "state": "EXPECTED",
      "receivedAt": null
    }
  ],
  "receivingComplete": false,
  "allTerminal": false
}
```

### POST /api/lots/{lotId}/mark-box-unpacked
Called after all items in a box have been scanned/counted.

**Request:**
```json
{
  "manifestCartonId": "BOX-0231-001"
}
```

**Response:**
```json
{
  "state": "UNPACKED",
  "lotReadyToClose": false
}
```

### POST /api/lots/{lotId}/close
Close lot → allocate costs. Gated: all boxes must be terminal.

## UI Flow: Quick Flow

### Screen: Receive Menu
- Button: "📦 Receive Boxes"
- Button: "📋 Unpack Items"

### Screen: Receive Boxes
1. **Lot selector** — dropdown of OPEN lots with box counts (e.g., "LOT-0231 · 32/47 boxes")
2. **Scan carton ID** — input field, large font
3. **Feedback** — "✓ BOX-0231-001 received" or "✗ Already scanned"
4. **Progress** — "32/47 boxes received"
5. **Buttons:**
   - "Not here?" → modal to mark box NOT_RECEIVED
   - "Damaged?" → modal to mark box REJECTED
   - "Done" → back to menu (even if not 47/47, warehouse may still receive late boxes)

Once all 47/47 scanned → "Receiving complete. Ready to unpack."

### Screen: Unpack Items
1. **Lot selector** — dropdown of FULLY_RECEIVED lots
2. **Box selector** — dropdown of boxes in RECEIVED state (or pre-select if coming from receive flow)
3. **Scan item** — product barcode or internal code
4. **MRP** — prompted if product never seen before
5. **Feedback** — "✓ Item added (count: 3)" or "⚠ Qty exceeds expected (4/3)"
6. **Buttons:**
   - "Next box" → clears item input, moves to next box in lot
   - "Done" → back to menu

After last item in last box scanned → "Box complete" → prompt "Close lot?"

## State Transitions (Backend Validation)

- EXPECTED → RECEIVED: any time during receiving
- RECEIVED → NOT_RECEIVED: operator action (receiving stage)
- RECEIVED → REJECTED: operator action (obvious damage, receiving stage)
- RECEIVED → UNPACKING: when first item scanned in box (unpacking stage)
- UNPACKING → UNPACKED: when all items in box scanned (backend sums manifest lines for box, compares to actual)
- UNPACKING → REJECTED: operator action (functional damage discovered during unpack)
- Any terminal → CLOSED (lot level): only when all boxes terminal (UNPACKED OR NOT_RECEIVED OR REJECTED)

## Cost Allocation Gate

Current `ConsignmentImporter` flow (lot close):
```
Lot.close() → sum items across boxes → weight by category/cost → allocate
```

New gate:
```
Lot.close() → verify all boxes in {UNPACKED, NOT_RECEIVED, REJECTED} → proceed
```

If any box not terminal → throw `IncompleteReceivingException`.

**Handling rejected boxes:**
- Rejected boxes (NOT_RECEIVED, REJECTED) contribute zero items to cost allocation.
- Cost pool is reduced by manifest value of rejected boxes.
- Remaining cost allocated only over UNPACKED boxes.
- Example: Lot cost ₹100, Box A (₹30 manifest) rejected, Box B (₹70 manifest) unpacked → allocate ₹70 to Box B's items only.

## Rejection Workflow (Decided: Both Stages)

- **Receiving stage:** Operator can mark box NOT_RECEIVED (never arrived) or REJECTED (obvious damage, unopened).
- **Unpacking stage:** Operator can mark box REJECTED if functional damage discovered mid-unpack (e.g., items inside are defective, water-soaked).

**Cost allocation:** Rejected boxes contribute zero items and zero cost to lot. Remaining boxes' items absorb the remaining cost pool.
