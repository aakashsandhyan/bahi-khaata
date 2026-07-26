# Manual Lot Management

## Why

Current receiving workflow assumes all lots have a manifest (expected products, quantities, tracking numbers). But unplanned bulk purchases (personal care lots, overstock liquidation, damaged-goods salvage) arrive without manifests — just a packing list (if lucky) or an invoice. Operator must manually enter each product's details and decide cost allocation. Today there's no way to do this; receiving is blocked on manifest import.

## What Changes

Add Lot Management screen to create manual (manifest-free) lots, and extend Receiving to handle manual product entry (no pre-created boxes/BoxReceipts). Operator workflow: create lot in Lot Management → go to Receiving → scan/enter products one by one → system allocates total cost → lot closes.

### New capabilities

- **lot-creation-ui**: Lot Management screen (list + "Create Lot" button, choose manifest vs manual)
- **manual-lot-creation-api**: POST endpoint to create manual lot (supplier, date, amount paid, allocation method)
- **manual-product-entry**: Receiving screen enhanced to accept manual product entry when lot has no manifest (name, category, quantity)
- **cost-allocation-manual**: Allocate total lot cost across manually-entered products (equal split or weighted by user estimate)

### Modified capabilities

- **receiving-overview**: Show lot type indicator (manifest-based vs manual) in list
- **receiving-detail**: If manual lot, show product-entry form instead of box scanner; accumulate as you enter
- **lot-list-endpoint**: Return lot type (manifest vs manual) in LotSummary response

## Capabilities Affected

- Receiving screen (new manual entry flow)
- LotSummary (new field: isManual)
- Lot creation (new API endpoint)

## Impact

- Operators can now receive unplanned lots without waiting for manifest import
- No data loss: cost is still tracked and allocated, products are searchable and priceable after lot closes
- Backward compatible: manifest-based lots unchanged

## Risks

- Cost allocation without manifests is guess-work; no enforcement. Operator estimates per-product value
- No box tracking for manual lots — different from manifest lots which track by tracking number
- Manual entry is slow vs barcode scanning; suitable only for small/diverse lots

## Acceptance

- Lot Management screen launches from App.tsx navbar
- Manual lot created, appears in Receiving overview with "manual" indicator
- Receiving detail shows product entry form (not box scanner)
- Products entered, cost allocated, lot closed successfully
- Lot appears in all subsequent screens (unpacking, catalog, etc.) with products marked as received
