# Tasks — in-hand-pricing-and-reprint

## 1. In-hand reconciliation (backend)

- [x] 1.1 `GoodsInCounting.reconcileBatchTo(batch, targetQty, at)` — add or remove to hit `targetQty`: up → `addCounted` + `ledger.receipt`; down → `removeCounted` + `ledger.adjustment`; zero delta → nothing. Reuses the existing count primitives so `quantity_received`, PINNED cost, and the ledger stay in step.
- [x] 1.2 `ScannedItem` gains `sellingPricePaise` (nullable). `ShelfPricing.toScannedItem` fills it from `product.getSellingPrice()`; `quantity` still carries the batch's current on-hand.
- [x] 1.3 `PriceExistingRequest` gains `inHandQuantity`.
- [x] 1.4 `ShelfPricing.saveExisting`: read `product.getSellingPrice()` **before** setting the price. If null (first pricing) → `reconcileBatchTo(batch, inHandQuantity)` (overwrite). If set (later) → add `inHandQuantity` to the batch (plus-only; 0 = no move). Then price/MRP as today.
- [x] 1.5 Tests: first pricing up (7→8, +1 receipt), first pricing down (7→5, −2 adjustment), later pricing add (4+3=7), later with 0 added = no ledger move, no-change = no move. Assert on-hand and PINNED cost total after each.

## 2. Pricing form — Expected + In-hand (frontend)

- [x] 2.1 Show the manifest **Expected** quantity for reference (read-only) on a scanned item.
- [x] 2.2 The quantity field means **In-hand total** when the item is not yet priced (`sellingPricePaise == null`) — default to the counted quantity; and **additional found** when already priced — default **0**, label it so, and send it as `inHandQuantity`.
- [x] 2.3 Confirm the label count still follows the in-hand/added quantity (labels for what's now on hand, per the hold-and-pair queue).

## 3. Reprint label by barcode

- [x] 3.1 Backend `GET /api/print-jobs/label-for?barcode=` → `{barcode, name, sellingPricePaise, mrpPaise}`; MRP resolved from the batch (as `sellableMrp` does). 404 unknown code; clear 4xx when the product has no price.
- [x] 3.2 Frontend **Reprint** view + nav tab: barcode box → resolved label card (name/price/MRP) → `QtyInput` → queue via the existing `queueLabel`. Show the refusal message for unknown/unpriced.
- [x] 3.3 Tests: lookup returns the current name/price/MRP; unknown and unpriced are refused.

## 4. Clearable quantity input

- [x] 4.1 `QtyInput` component — raw text while typing (empty allowed), clamp to a whole number ≥ min on blur, `onChange(n)`. (Prototyped on `fix/qty-input-clearable` — bring it in.)
- [x] 4.2 Apply it in: the count pane, both pricing quantities, the review queue, and manual receive. Remove the `parseInt(value) || default` handlers.

## 5. Verify + ship

- [x] 5.1 Full backend suite + ArchUnit green; frontend `tsc` clean.
- [ ] 5.2 Local end-to-end: first-price a product (overwrite), re-price to add, re-price with 0 to fix MRP (no move), reprint by barcode, clear-and-retype a qty.
- [x] 5.3 Deploy to the shop — this **mutates stock on real data**, so back up first and verify on-hand moves only as expected; watch a down-adjustment case.
- [ ] 5.4 `/opsx:sync` + `/opsx:archive` once shipped.
