# Bill printing

## Why

The till can build a cart but has no way to **complete a sale** — nothing is recorded, stock is never decremented (the `SALE` ledger movement exists but is never written), and no bill is ever produced. Customers need a printed bill and the shop needs a record of what sold; both require a sale-completion step that does not exist today.

## What Changes

- Add **complete-sale** to the till: finalize a cart into a persisted, immutable `Sale` (with snapshotted lines), assign a running bill number, capture the payment method, and write `SALE` ledger movements that decrement stock.
- **Selling never blocks on stock.** The counter is ground truth; a rough count must not refuse to sell a physical item. On-hand is allowed to go negative — that negative is the true record and the signal to recount later.
- Add a **bill/receipt** printed on an **80mm ESC/POS thermal receipt printer** (a second device, separate from the TSC TE-244 label printer), rendered from the persisted sale and **reprintable** by bill number.
- The bill is **settings-driven**: shop name/address/GSTIN, bill title, declaration, footer are admin-editable. Under the GST **composition scheme** the bill is a **Bill of Supply** with no tax lines and the mandatory composition declaration. Actual GST treatment is pending the shop's CA, so no tax is computed on the bill now.
- Add a **Sales** screen: list recent sales (bill no, date, total, method) and reprint any bill.
- Payment records the **method only** (Cash / UPI / Card). COGS is captured on the ledger via FIFO for later margin reporting but **never appears on the customer bill**.

## Capabilities

### New Capabilities
- `checkout`: completing a sale — persisting an immutable `Sale` with snapshotted lines, a running bill number, payment method, idempotent completion, and listing/reprinting past sales.
- `receipt-print`: printing a bill/receipt on an ESC/POS thermal printer — a dedicated receipt driver and its config, a template that renders a sale as a Bill of Supply / receipt from editable bill settings, printing on completion, and reprinting from a stored sale.

### Modified Capabilities
- `stock-ledger`: a completed sale writes `SALE` movements FIFO across the product's batches, decrementing stock; on-hand may go negative when the sale exceeds the counted quantity, and that negative stands as the record.

## Impact

- **Backend** — `checkout`: `Checkout.complete`, new `Sale`/`SaleLine` entities + repositories, sales queries. `print`: `ReceiptPrinterDriver` + ESC/POS implementation, `ReceiptTemplateService`, `receipt_printer_config`, `bill_settings`. `inventory`: FIFO `SALE` ledger consumption + COGS.
- **Contracts** — `SaleView`, `SaleLineView`, `SaleSummary`, `CompleteSaleRequest`, `BillSettings`, `ReceiptPrinterConfig`, a payment-method enum.
- **Migrations** — `sale`, `sale_line`, `receipt_printer_config`, `bill_settings`.
- **Frontend** — Checkout gets a complete-sale + payment-method flow and a print/reprint confirmation; a new Sales screen; admin screens for the receipt printer config and bill settings.
- **Hardware** — a new 80mm ESC/POS receipt printer at the counter (LAN raw-9100 or USB).
- **Dependencies** — `checkout → print` (trigger the receipt) and `checkout → inventory` (write the SALE ledger). No new external libraries (ESC/POS is raw bytes over a socket or javax.print).
