# Warehouse Receiving & Unpacking Flow

## Overview

The receiving workflow is a two-stage process:
1. **Receiving**: Verify all cartons for a lot have physically arrived
2. **Unpacking**: Open cartons, count items, capture MRP, allocate costs

## State Machine

```
EXPECTED (manifest imported)
    ↓
RECEIVED (carton scanned at dock)
    ├→ NOT_RECEIVED (marked by operator, not arrived)
    ├→ REJECTED (marked by operator, obvious damage)
    └→ UNPACKING (items being counted)
        ├→ UNPACKED (all items counted, MRP captured)
        └→ REJECTED (functional damage found during unpack)

Terminal states: UNPACKED, NOT_RECEIVED, REJECTED
```

## Database Schema

### `box_receipt` Table
Tracks the physical receipt of each carton in a lot.

```sql
CREATE TABLE box_receipt (
  id CHAR(36) PRIMARY KEY,              -- UUID
  lot_id CHAR(36) NOT NULL,              -- FK to lot
  manifest_carton_id TEXT NOT NULL,      -- Tracking ID from manifest
  box_state TEXT DEFAULT 'EXPECTED',     -- Current state (enum)
  received_at TEXT,                      -- When scanned (ISO-8601)
  rejected_reason TEXT,                  -- Why rejected (if REJECTED)
  created_at TEXT,
  updated_at TEXT
);

CREATE INDEX idx_box_receipt_lot_id ON box_receipt(lot_id);
CREATE INDEX idx_box_receipt_lot_carton ON box_receipt(lot_id, manifest_carton_id);
CREATE INDEX idx_box_receipt_state ON box_receipt(box_state);
```

### `lot` Table Addition
New column: `receiving_complete BOOLEAN`
Tracks whether all boxes for a lot are in terminal state.

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
  "receivedAt": "2026-07-26T10:30:00Z",
  "lot": {
    "id": "lot-uuid",
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
  "manifestCartonId": "BOX-0231-042",
  "reason": "NOT_RECEIVED" | "REJECTED",
  "notes": "Water damage, unopened"
}
```

### GET /api/lots/{lotId}/boxes
List all boxes for a lot with state counts.

**Response:**
```json
{
  "boxes": [
    {
      "manifestCartonId": "BOX-0231-001",
      "state": "RECEIVED",
      "receivedAt": "2026-07-26T10:30:00Z",
      "rejectedReason": null
    }
  ],
  "receivingComplete": false,
  "allTerminal": false,
  "counts": {
    "expected": 47,
    "received": 32,
    "unpacked": 5,
    "rejected": 1,
    "notReceived": 2
  }
}
```

### POST /api/lots/{lotId}/mark-box-unpacked
Mark a carton's unpacking complete.

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
  "lotReadyToClose": false,
  "nextBox": {
    "manifestCartonId": "BOX-0231-002",
    "state": "RECEIVED"
  }
}
```

## Cost Allocation

**Gate**: Lot can only close when ALL boxes are in terminal state (UNPACKED, NOT_RECEIVED, or REJECTED).

**Cost exclusion**: Boxes in NOT_RECEIVED or REJECTED state contribute:
- Zero items to the lot's stock
- Zero rupees from the cost pool

The cost pool is reduced by the manifest value of rejected boxes, and remaining cost is allocated only across UNPACKED boxes' items.

**Example**:
- Lot total cost: ₹945,662
- 47 boxes expected, 45 unpacked, 1 rejected (₹30k), 1 not-received (₹20k)
- Allocatable cost: ₹945,662 - ₹30k - ₹20k = ₹895,662
- This ₹895,662 distributed across 45 boxes' items only

## Terminal Usage

### Quick Flow UI

**Receiving Screen:**
1. Select lot from dropdown
2. Scan carton ID
3. See progress (32/47)
4. Click "Not received" or "Damaged" for exceptions
5. Click "Done" when finished

**Unpacking Screen:**
1. Select lot
2. Select box to unpack
3. Scan item (barcode or product code)
4. Enter MRP if product new
5. Click "Next box" when box complete
6. Click "Done" when lot complete

## Testing

Run E2E tests:
```bash
./gradlew :backend:test --tests ReceivingE2ETest
```

Manual flow:
1. Start backend: `./gradlew :backend:run`
2. Start terminal: `./gradlew :terminal:run`
3. Import lot via manifest endpoint
4. Navigate to Receiving screen
5. Scan each carton ID (use mock IDs from manifest)
6. Verify progress updates
7. Switch to Unpacking screen
8. Scan items, add MRP
9. Close lot
10. Verify costs allocated to items

## Deployment Notes

- Migrations V28–V30 add `box_receipt` table and `receiving_complete` flag
- No breaking changes to existing APIs
- Cost allocation gates on receiving completion (new validation)
- Terminal UI stubs ready for production API integration

## Future Work

- Integrate real API calls in terminal screens (currently stubs)
- Add MRP modal for new products during unpacking
- Add damage/issue categorization during remediation
- Generate scanning labels (ZXing) from product barcodes
- Add role-based access control (admin sees costs/margins, operators don't)
