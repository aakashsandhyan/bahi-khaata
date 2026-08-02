# Bill printing — design

**Date:** 2026-08-02
**Status:** approved design (brainstorming output). Feeds an OpenSpec change (expanded workflow) for the durable spec + tasks.

## Problem

The till (`/api/checkout`) can open a cart, scan, edit lines, and clear — but there is **no way to complete a sale**. No sale is recorded, stock is never decremented (the `MovementType.SALE` ledger factory exists but the till never calls it), and `CartView.taxIsPlaceholder` says outright that no invoice is issued from it. A bill is the output of a completed sale, so this feature is the **whole chain**: complete sale → record it + decrement stock → render bill → print it → reprint/records.

## Scope decisions (from brainstorming)

- **Whole chain**, not just printing: add the missing sale-completion.
- **Hardware:** a dedicated **80mm ESC/POS thermal receipt printer** (the POS standard), separate from the TSC TE-244 label printer.
- **Bill content:** a **simple customer receipt now**, laid out so tax specifics slot in later. GST specifics are pending the shop's CA.
- **Composition scheme (likely):** the shop is considering the GST composition scheme. A composition dealer issues a **"Bill of Supply"** (not a Tax Invoice), **collects no GST**, shows **no CGST/SGST/per-line tax**, and must carry its **GSTIN** and the mandatory declaration *"Composition taxable person, not eligible to collect tax on supplies."* This matches the simple-receipt design; it only fixes the header wording. Kept configurable so the CA's final answer (composition vs regular) is a settings change, not code.
- **Payment:** capture the **method only** — Cash / UPI / Card. No tendered/change math.
- **Reprint + records:** persist every sale; a **Sales screen** lists recent sales and reprints any bill by number.
- **COGS:** tracked via **FIFO** (cheap; the ledger already carries a cost field; enables margins later). Never appears on the customer bill.

## Data model

New tables:

- `sale` — `id` (UUID), `bill_no` (running integer, rendered `BB-000123`), `payment_method` (`CASH`/`UPI`/`CARD`), `subtotal_paise`, `saving_paise`, `tax_paise` (0 for now — the tax slot), `total_paise`, `operator_name` (per-device, like pricing), `created_at`.
- `sale_line` — `id`, `sale_id`, `product_id`, and **snapshots**: `name`, `barcode`, `mrp_paise`, `unit_price_paise`, `quantity`, `line_total_paise`, `saving_paise`.

Snapshotting mirrors the self-contained `PrintJob`: a later price change must never alter a past bill.

`bill_no` = `MAX(bill_no) + 1` inside the completion transaction — safe under the single serialized SQLite connection.

## Complete-sale flow

`POST /api/checkout/cart/{cartId}/complete { paymentMethod, operatorName }`:

1. **Guard only that the cart is non-empty and every line resolves to a priced product.** Do **not** block on stock availability — the counter is ground truth; a rough count must never refuse to sell a physical item.
2. In one transaction: create `sale` + `sale_line`s (snapshot), assign `bill_no`, and for each line write **`MovementType.SALE` ledger entries FIFO across the product's batches**, capturing COGS per batch. This decrements till stock.
   - **Overshoot is allowed:** if the counts show fewer units than sold, the extra units attribute to the last (newest) batch, driving *that batch* negative, COGS at its pinned cost. A negative on-hand is a true, timestamped fact — "sold more than the count said" — and becomes the signal to recount (natural home for a future "negative on-hand" report). The ledger bends to the sale, never the other way.
3. Mark the cart completed → re-completing is rejected (idempotent).
4. **After commit**, print the receipt. A print failure never rolls back a recorded sale.

## Receipt printing

- **`ReceiptPrinterDriver`** — new interface, `printReceipt(byte[] escpos)`, separate from the label `PrinterDriver` (TSPL ≠ ESC/POS → two clean drivers).
- **ESC/POS driver + `receipt_printer_config`** table, mirroring the label `printer_config` (address, transport, enabled, test_status, last_tested_at) — a **second physical printer**, with an admin screen + "test print". Transport default **LAN raw socket (`ip:9100`)**, with **USB** (javax.print) as the alternative; the config holds both, so no lock-in. *(Open: which the shop's printer uses.)*
- **`ReceiptTemplateService`** — renders a `Sale` → ESC/POS bytes for an 80mm roll (~48 chars/line, Font A): shop header (name, GSTIN, bill title, declaration — from settings), bill no. + date/time, one line per item (name / `qty × price` / line total), **total saved vs MRP** (the brand promise), grand total (bold/double-height), payment method, footer, partial cut.
- **Bill settings** config — shop name, address, GSTIN, **bill title** (`Bill of Supply` | `Tax Invoice`), **declaration text**, footer — admin-editable. The template reads these, so the CA's decision is a settings change.
- **Print path:** synchronous on complete; **reprint re-renders from the persisted `Sale`** (never a live cart), so an old bill reprints exactly as issued.

## Till UX

- **Checkout:** a **Complete sale** button on a non-empty cart → pick **Cash / UPI / Card** → sale records, bill prints, confirmation shows **bill no. + total** with a **Reprint** if print failed → **New sale** resets the cart. Operator = the per-device name (like pricing).
- **Sales screen (new "Sales" tab):** recent sales (date/time, bill no, total, method, item count), **Reprint** per row, search by bill no.

## API / contracts

- `POST /api/checkout/cart/{cartId}/complete { paymentMethod, operatorName }` → `SaleView` (billNo, total, saving, method, lines, `printStatus`)
- `GET /api/sales?limit=` → `SaleSummary[]`; `GET /api/sales/{billNo}` → `SaleView`
- `POST /api/sales/{id}/reprint` → re-render + print
- `GET/PUT /api/admin/bill-settings`; `GET/PUT /api/admin/receipt-printer-config` + `POST …/test`
- Contracts: `SaleView`, `SaleLineView`, `SaleSummary`, `CompleteSaleRequest`, `BillSettings`, `ReceiptPrinterConfig`.

## Error handling — never lose a sale to a printer

- Print fails (offline / jam / not configured): the sale is already committed → response flags `printFailed` → till shows "Sale BB-000123 recorded — Reprint". Fix the printer, reprint from the sale.
- Empty cart / unpriced line → rejected **before** recording, with a clear message.
- `bill_no` collisions: prevented by MAX+1 in the transaction on the single connection.

## Testing

- **Unit:** complete writes the sale + FIFO SALE ledger (stock down, **negative allowed** on overshoot); COGS correct; **snapshot immutability** (a later price change does not alter a past bill); monotonic `bill_no`; empty-cart + re-complete rejected.
- **Template:** renders the expected ESC/POS structure + settings (title / declaration / GSTIN present, no tax lines under composition).
- **Driver:** mocked socket connection/test; the **print-failure path leaves the sale committed**.
- **Reprint:** re-renders identical bytes from the stored sale.

## Module placement

- `checkout` — `Checkout.complete`, `Sale`/`SaleLine`, `SaleRepository`, sales queries.
- `print` — `ReceiptPrinterDriver`, the ESC/POS impl, `ReceiptTemplateService`, `receipt_printer_config`.
- Bill settings — a small config (in `print` or a settings module).
- Dependencies: `checkout → print` (trigger receipt) + `checkout → inventory` (FIFO SALE ledger). All backend-internal; ArchUnit only guards the top-level modules.

## Open items

- **GSTIN + tax structure** — pending the CA. Composition → "Bill of Supply" + declaration, no tax lines. Regular → "Tax Invoice" + (later) per-line tax. Both are settings/derivations, not restructure.
- **Receipt printer transport** — USB vs LAN — decided by the printer the shop buys; config supports both.
