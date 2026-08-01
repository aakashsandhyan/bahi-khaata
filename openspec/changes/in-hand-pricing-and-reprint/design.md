## Context

Stock lives on batches and in an append-only `stock_ledger`; on-hand is `SUM(quantity)` from the ledger, never a stored counter. Counting a product (`GoodsInCounting.addToBatch`) does two things together: `batch.addCounted(qty)` (which re-pins a PINNED batch's cost total) **and** `ledger.save(receipt(qty))` — so the batch's `quantity_received` and the ledger stay in step. `GoodsInCounting.undoCount` / `GoodsRemediation` do the mirror with `removeCounted` + a negative `adjustment`.

Pricing today (`ShelfPricing.saveExisting`) is stock-neutral: it sets the price and MRP and mints/keeps a barcode, writing no ledger. `ScannedItem` already carries the batch's `quantity` (added for the label-count feature). The print queue is self-contained (`queueLabel` carries barcode/name/price/mrp), and the executor pairs and prints (now that scheduling is on).

## Goals / Non-Goals

**Goals:**
- Make the in-hand quantity entered at pricing the count of record, reconciled through the ledger, keeping `batch.quantity_received`, cost, and on-hand consistent.
- First pricing overwrites; a later pricing adds; neither corrupts a PINNED batch's cost.
- Reprint a label from a barcode alone, and give the app its one barcode search.
- Quantity fields that can be cleared and retyped.

**Non-Goals:**
- Changing how unpacking counts (it stays the rough first pass).
- A reconciliation history/review UI for down-adjustments (a plain ledger adjustment is enough for now).
- Touching checkout, FIFO, or valuation beyond what the reconciled on-hand naturally feeds.

## Decisions

### 1. Reconcile the scanned batch, reusing the counting primitives
The in-hand quantity reconciles the **batch that was scanned** (`item.batchId`) — the lot's batch — not a product-wide figure; the product total stays the sum across its batches. Reconciliation goes through the same primitives counting uses, so the two views never diverge and PINNED cost re-pins correctly:
- Compute the target and the current `onHandForBatch(batchId)`.
- **Up** (`delta > 0`): `batch.addCounted(delta)` + `ledger.receipt(delta)`.
- **Down** (`delta < 0`): `batch.removeCounted(-delta)` + `ledger.adjustment(-delta)`.
- **Zero**: nothing.

A small `GoodsInCounting.reconcileBatchTo(batch, targetQty, at)` (add or remove to hit a target) is the natural home, called from `ShelfPricing`.

### 2. First-vs-later keyed on "already priced"
The signal is `product.getSellingPrice()` read **before** this save sets it:
- **null → first pricing**: the entered quantity is the in-hand total → reconcile the batch **to** that total (overwrite; the delta may be + or −).
- **non-null → later pricing**: the entered quantity is pieces found → reconcile **by** adding it (plus-only; 0 = no move). A down value is not offered on this path.

### 3. Carry the signals on ScannedItem and the request
- `ScannedItem` gains `sellingPricePaise` (nullable — null means not yet priced) so the form knows first-vs-later, and keeps `quantity` as the current batch on-hand to default the field.
- `PriceExistingRequest` gains an `inHandQuantity` (the total on first pricing, the amount to add on a later one). The frontend labels the field and sets the default from `sellingPricePaise`; the backend applies rule (2).

### 4. Reprint by barcode — a thin lookup + the existing queue
`GET /api/print-jobs/label-for?barcode=` resolves the barcode → product; returns `{barcode, name, sellingPricePaise, mrpPaise}` with MRP from the batch (the same newest-labelled-then-any resolution `sellableMrp` uses). 404 for an unknown code; 409/400 with a clear message when the product has no price. The Reprint screen calls it, shows the card, takes a quantity, and calls the existing `queueLabel` — no stock, no pricing.

### 5. One QtyInput everywhere
A `QtyInput` React component holds the raw text while typing (empty allowed) and commits a clamped whole number on blur, calling `onChange(n)`. Replaces the `parseInt(value) || default` inputs in the count pane, both pricing quantities, the review queue, and manual receive. (Already prototyped on `fix/qty-input-clearable`.)

## Risks / Trade-offs

- **Pricing now mutates stock** on live shop data — the significant change. Mitigated by reconciling through the audited ledger (every move is an entry) and by making a later-pricing plus-only (a re-price to fix a figure, with 0 added, moves nothing).
- **A down-adjustment on first pricing** (in-hand below counted) writes a correcting negative with no separate flag. Acceptable because it is a genuine correction of an over-count and the ledger records it; if it later needs review, a flag can be added without reworking this.
- **Batch vs product scope**: reconciling the scanned batch means the in-hand corrects *that lot's* count. A product spread across lots is corrected one batch at a time — matches how it is priced (per lot), and keeps cost per batch honest.
- **Concurrent reconciliation**: SQLite serialises writes, so a double-submit is the risk the count pane already guards against; the pricing save is a single request, lower exposure.
