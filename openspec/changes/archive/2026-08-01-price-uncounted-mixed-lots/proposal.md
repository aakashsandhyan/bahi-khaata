## Why

A mixed/bulk lot that the shop does not count cannot be priced. The pricing workbench lists
only lots that already hold a counted batch (`select distinct b.lot.id from Batch b where
b.product.sellingPrice is null`), and hand-pricing — the intended path for uncounted, no-code
goods — is only reachable *after* a lot is selected there. A freshly added lot with no counted
stock has no batch, so it never appears in the picker, so it can never be hand-priced. The one
path built for uncounted goods is locked behind a list that excludes exactly those lots.

A second, independent gap sits behind it: even once such a lot could be selected, the category
picker for hand-add is empty, because categories are derived only from the lot's batches. The
`shelf-pricing` spec already requires a fallback to the full category list "when the lot has
none yet" — but the implementation never did it. That is a spec-vs-code drift, fixed here, not
a new requirement.

## What Changes

- The pricing lot picker SHALL also list **open lots that have no counted stock yet**, so a
  mixed lot that is never counted can be selected and hand-priced into. Lots whose counted
  stock is entirely priced still drop off as today.
- Implement the already-specified category fallback: when the selected lot has no batches, the
  hand-add category choices SHALL be the full category list rather than an empty set. (Brings
  the code in line with the existing spec — no requirement change.)
- No schema change. Hand-add (`saveManual`) already creates the batch and moves stock; this
  change only makes the lot reachable and the category pickable.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `shelf-pricing`: the "Pricing starts from a lot" requirement broadens which lots the workbench
  lists — from only lots with counted-but-unpriced stock, to also include open lots with no
  counted stock yet, so uncounted mixed lots can be hand-priced.

## Impact

- **Backend**: `ShelfPricing.lots()` — widen the query/selection to include open, batch-less lots
  alongside lots with unpriced counted stock. `ShelfPricing.categoriesForLot()` — fall back to the
  full category list when the lot has no batches.
- **Frontend**: none required — `PricingWorkbench` already renders whatever `lots()` returns and
  already offers "Add by hand" once a lot is selected; the empty-lot case simply starts working.
- **Out of scope**: any change to counting, receiving, manifest import, or the manual-lot
  expectation flow; dropping fully-priced open lots (they stay until closed, as today).
