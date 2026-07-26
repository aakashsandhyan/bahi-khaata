# Design: Manual Lot Management

## Context

Bachat Baazar receives goods in two ways:

1. **Manifest-based**: Supplier sends packing list (expected products, quantities, boxes). Import creates Lot + Boxes + ExpectedLines. Receiving confirms receipt box-by-box.

2. **Manual**: Bulk purchase with no detailed manifest (e.g., "500 units personal care, ₹50,000 total"). No way to record. Operator has invoice + physical goods, needs to create a lot and manually enter products as they arrive.

This change handles case 2.

## Goals

- **Operator can create manual lots** without waiting for manifest import
- **Manual product entry** in Receiving (no pre-created boxes/tracking numbers)
- **Cost allocation** across products (system-assisted guessing)
- **Backward compatible** — manifest workflow unchanged

## Non-Goals

- Barcode scanning for manual lots (too slow, not suitable)
- Automatic cost allocation based on product history (nice-to-have; deferred)
- Multi-lot receiving (one lot at a time)

## Decisions

### 1. Manual lots have no boxes

**Why**: Manifest lots track by tracking number because the supplier provided them. Manual lots don't have tracking numbers. Adding "fake" tracking numbers (BOX-1, BOX-2, ...) adds complexity with no benefit — operator doesn't care about box IDs for a bulk purchase, only final product counts.

**How**: Manual lots skip BoxReceipt creation. Instead, manual product entry goes straight to expected lines. No box scanner, no box state machine — just product name/qty form.

**Alternative**: Add synthetic box IDs; rejected because extra layer of indirection adds confusion without value.

### 2. Cost allocation: equal split or user-estimated weight

**Why**: Without a manifest, operator doesn't know per-product cost. Three options: (a) equal split (each product gets total cost / count), (b) operator estimates per-product value, (c) defer to closing (cost is weighted by final counts at close time, like manifest lots).

**How**: Start with equal split (simplest). Optional: operator can override with estimated weight per product. At lot close, use final counted quantities to reapportion if entries differ from receipt.

**Alternative rejected**: Automatic weight based on product history (requires product lookup, risky for new products).

### 3. Lot type stored as isManual flag

**Why**: Schema simplicity. One boolean flag instead of enum.

**How**: `Lot.isManual` boolean. When true, skip box state logic; when false, use existing box workflow.

**Alternative**: Separate `ManualLot` entity; rejected because most queries just filter by isManual anyway.

### 4. Lot Management is separate from Receiving

**Why**: Lot creation and lot receiving are distinct workflows. Lot Management creates lots (batch operation, administrative); Receiving fills in product details (operative, item-by-item). Separating them keeps each focused.

**How**: New LotManagement.tsx screen for creation. Receiving unchanged except for manual product entry support. Both query same /api/lots endpoint.

**Alternative**: Add create button to Receiving overview; rejected because context mixing (creating and receiving in one screen is confusing).

## Data Model

```
Lot
  ├─ id (UUID)
  ├─ supplier (String)
  ├─ receivedOn (LocalDate)
  ├─ amountPaid (Money)
  ├─ freight (Money, 0 for manual)
  ├─ allocationMethod (RELATIVE_MRP)
  ├─ isManual (boolean) ← NEW
  ├─ isOpen (derived: unpacked count < expected count)
  └─ isReceivingComplete (derived: all boxes terminal state OR isManual && all entries received)

ExpectedLine (unchanged)
  ├─ code (String)
  ├─ name (String)
  ├─ quantity (int)
  └─ cost allocation (at lot close)

BoxReceipt (unchanged, not created for manual lots)
```

## API Changes

**POST /api/lots/manual** (NEW)
```json
{
  "supplier": "string",
  "receivedOn": "YYYY-MM-DD",
  "amountPaidPaise": "number",
  "allocationMethod": "RELATIVE_MRP"
}
→ LotSummaryDto
```

**GET /api/lots** (UPDATED)
- Added: `isManual: boolean` in response

**POST /api/lots/{id}/add-product** (NEW, for manual lots)
```json
{
  "code": "string",
  "name": "string",
  "quantity": "number",
  "estimatedCostPaise": "number | null"
}
→ {success: boolean, totalProducts: number, totalQuantity: number}
```

## Frontend Changes

**LotManagement.tsx** (NEW)
- List: open + closed lots, type indicator (manifest vs manual)
- Create button → modal: supplier, date, amount → POST /api/lots/manual
- Tab: In Progress / Complete (same pattern as Receiving)

**Receiving.tsx** (MODIFIED)
- Overview: same, but show lot type indicator
- Detail: if isManual, show product-entry form instead of box scanner
  - Form: code (optional), name, category, qty, estimated cost (optional)
  - "Add Product" button → accumulate
  - "Done" button → close lot

**types.ts** (UPDATED)
- `LotSummary`: add `isManual: boolean`

**api.ts** (UPDATED)
- `lots.createManual(supplier, receivedOn, amountPaidPaise)`
- `lots.addProduct(lotId, code, name, quantity, estimatedCostPaise)`

## Migrations

- Add `is_manual` boolean column to `lot` table, default false (backward compatible)

## Testing

1. Create manual lot via API (POST /api/lots/manual) → appears in list as manual
2. Add products via API (POST /api/lots/{id}/add-product) → accumulate
3. Close lot → cost allocates across products
4. Lot appears in subsequent screens (Receiving, Unpacking, Catalog) with products marked received
5. Manifest lots unchanged (regression test)

## Open Questions

1. Should operator be able to edit products after adding them? (defer: no, recreate)
2. Should cost allocation be re-weighted at close time based on actual counts? (yes, but defer implementation to goods-in-from-manifest redesign)
3. What category options for manual products? (use existing Category enum; if new category needed, add ad-hoc)
