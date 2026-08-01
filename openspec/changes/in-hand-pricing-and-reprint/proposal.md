## Why

Unpacking counting is a rough first pass. On a liquidation delivery, products get missed or mis-counted — a line counted 7 against an expected 11, items arriving without a clean reference, a scan landing on the wrong product. The count that reaches the shelf is therefore unreliable, yet it is what the stock ledger trusts today.

The **accurate** count happens later, at **pricing** — that is the moment someone actually holds each product, one at a time, to price and label it. So pricing, not unpacking, is where the true in-hand count should be captured and become the stock of record. Today pricing writes no stock at all (it only sets a price and prints a label), so that accurate count is thrown away.

Two smaller gaps ride along with this. A label printed with a wrong figure can only be corrected by re-running the whole pricing flow — there is no way to look a product up by its barcode and just reprint. And every quantity input in the app snaps back to its default the instant you delete the last digit, so a number can never be cleared, only overwritten.

## What Changes

- **In-hand count at pricing.** The pricing form shows the manifest's **Expected** quantity for reference and takes an **In-hand** quantity that reconciles the stock ledger:
  - **First time a product is priced** (not yet priced): the in-hand quantity is the true total and **overwrites** on-hand — up or down from whatever unpacking counted. The field defaults to the counted quantity, so a correct count is a one-tap confirm.
  - **Re-pricing an already-priced product**: the quantity is **additional pieces found** (defaults to 0) and is **added** to stock — plus-only, never a reduction, because a second scan means more of the same product turned up. Leaving it at 0 lets you fix the price or MRP with no stock movement.
- **Reprint label by barcode.** A standalone screen: scan or type a `BBZ-…` barcode, see the resolved product (name, price, MRP), enter how many, and print — through the existing self-contained print queue. No pricing flow, no stock touched. This is also the only place in the app you can search by barcode.
- **Clearable quantity inputs.** Every quantity field lets you delete the digit and type a new one; the value is clamped to a whole number only on blur, not on each keystroke.

## Capabilities

### New Capabilities
<!-- none — the reprint screen extends label-print, the in-hand count extends shelf-pricing/stock-ledger -->

### Modified Capabilities
- `shelf-pricing`: pricing captures an in-hand quantity that reconciles stock — overwrite on the first pricing, add on later ones; the form surfaces the expected quantity for reference; quantity entry is clearable.
- `stock-ledger`: a pricing reconciliation writes ledger movements — a receipt when the in-hand count exceeds current on-hand, a correcting adjustment when it is lower (first pricing only); later pricings only add.
- `label-print`: labels can be reprinted for an already-priced product by its barcode, resolved to its current name, price, and MRP, without re-pricing.

## Impact

- **Backend**: `ShelfPricing.saveExisting` (and the scanned-item flow) gains in-hand reconciliation writing to the stock ledger — a departure from today's stock-neutral pricing; a first-vs-later rule keyed on whether the product is already priced. A small lookup endpoint `GET /api/print-jobs/label-for?barcode=` returning `{barcode, name, sellingPricePaise, mrpPaise}` (MRP resolved from the batch, like `sellableMrp`). `ScannedItem` already carries the counted quantity (from the qty feature) to default the field.
- **Frontend**: pricing form shows Expected + In-hand with the first/later semantics; a new "Reprint" nav view; a shared `QtyInput` component replacing the `parseInt(value) || default` pattern in the count pane, pricing, review queue, and manual receive.
- **Data**: pricing now mutates on-hand — a real behaviour change on live shop data. The down-adjustment path (in-hand below counted) writes a correcting negative; whether it is flagged for review is a design decision.
- **Migrations**: none expected (ledger is append-only; no schema change).
