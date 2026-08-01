# Tasks — bill-printing

## 1. Data model + migrations

- [ ] 1.1 Migrations: `sale` (id, bill_no, payment_method, subtotal_paise, saving_paise, tax_paise, total_paise, operator_name, created_at), `sale_line` (id, sale_id FK, product_id, name, barcode, mrp_paise, unit_price_paise, quantity, line_total_paise, saving_paise), with a CHECK on payment_method (CASH/UPI/CARD) and an index on bill_no.
- [ ] 1.2 Migrations: `receipt_printer_config` (mirror `printer_config` — address, transport, enabled, test_status, test_error, last_tested_at) and `bill_settings` (single row — shop_name, address, gstin, bill_title, declaration, footer), seeded with sensible blanks/defaults.
- [ ] 1.3 Entities + repositories: `Sale`, `SaleLine` (immutable — no setters on snapshot fields), `ReceiptPrinterConfig`, `BillSettings`; `ddl-auto=validate` passes.
- [ ] 1.4 Contracts: `PaymentMethod` enum, `CompleteSaleRequest`, `SaleView`, `SaleLineView`, `SaleSummary`, `ReceiptPrinterConfig`, `BillSettings`.

## 2. SALE ledger + COGS (inventory)

- [x] 2.1 FIFO consumption: sell N units of a product → `MovementType.SALE` entries drawn oldest-batch-first, each carrying that batch's cost as COGS; reuse/extend existing FIFO helpers (`findByProductIdInFifoOrder`, `StockLevels`).
- [x] 2.2 Overshoot: units beyond counted on-hand attribute to the newest batch and drive it negative — no exception, no clamp. On-hand may go negative.
- [x] 2.3 Tests: FIFO split across batches with correct COGS; overshoot drives the newest batch negative; product with a single batch; assert on-hand + per-batch on-hand after.

## 3. Sale completion (checkout)

- [ ] 3.1 `Checkout.complete(cartId, paymentMethod, operatorName)`: in one transaction validate (non-empty cart, every line priced), create `Sale` + snapshotted `SaleLine`s, assign `bill_no = MAX+1`, write the SALE ledger (section 2), mark the cart completed.
- [ ] 3.2 Guards: empty cart rejected; unpriced line rejected naming the item; re-completing a completed cart rejected (idempotent).
- [ ] 3.3 Endpoint `POST /api/checkout/cart/{cartId}/complete` → `SaleView` (with `printStatus`); exception handlers (400/409) matching the checkout controller.
- [ ] 3.4 Tests: complete records sale + lines + ledger; snapshot immutability (later price change doesn't alter the sale); monotonic bill_no; empty-cart + unpriced + re-complete rejected.

## 4. Receipt printing (print)

- [ ] 4.1 `ReceiptPrinterDriver` interface (`printReceipt(byte[])`, `test()`) + ESC/POS implementation over the configured transport (LAN raw-9100 default; USB via javax.print alternative).
- [ ] 4.2 `receipt_printer_config` admin: `GET/PUT /api/admin/receipt-printer-config` + `POST …/test`; mirror the label printer-config controller + screen.
- [ ] 4.3 `bill_settings` admin: `GET/PUT /api/admin/bill-settings`.
- [ ] 4.4 `ReceiptTemplateService.render(Sale) → byte[]`: 80mm ESC/POS — header (name/GSTIN/bill title/declaration from settings), bill no + date/time, item lines (name / qty×price / line total), total saved vs MRP, grand total (bold/double-height), payment method, footer, partial cut. No tax lines.
- [ ] 4.5 Print on completion AFTER commit; `printFailed` flagged on `SaleView` if the printer is offline/unconfigured — never rolls back the sale.
- [ ] 4.6 Tests: template renders expected ESC/POS structure + settings (title/declaration/GSTIN, no tax lines); driver test mocked; print-failure path leaves the sale committed.

## 5. Sales queries + reprint

- [ ] 5.1 `GET /api/sales?limit=` → `SaleSummary[]` (newest first); `GET /api/sales/{billNo}` → `SaleView`.
- [ ] 5.2 `POST /api/sales/{id}/reprint` → re-render from the stored sale + print; returns print result.
- [ ] 5.3 Tests: list + lookup; reprint re-renders identical bytes from the persisted sale.

## 6. Frontend

- [ ] 6.1 Checkout: a **Complete sale** button on a non-empty cart → pick Cash/UPI/Card → complete → confirmation (bill no + total) with **Reprint** if `printFailed` → **New sale**. Reuse the per-device operator name (like pricing).
- [ ] 6.2 New **Sales** tab: recent sales list (bill no, date, total, method), search by bill no, Reprint per row.
- [ ] 6.3 Admin: receipt-printer-config screen (+ test) and bill-settings screen; api + types.

## 7. Verify + ship

- [ ] 7.1 Full backend suite + ArchUnit green; `tsc` + `vite build` clean.
- [ ] 7.2 Local end-to-end: build a cart, complete (each payment method), verify sale + ledger (incl. an overshoot → negative on-hand), reprint by bill no.
- [ ] 7.3 Shop rollout notes: install/configure the 80mm ESC/POS receipt printer, set bill settings (GSTIN/declaration per the CA), test print; this is the first thing that **decrements real stock at the till** — verify on-hand moves as expected.
- [ ] 7.4 `/opsx:verify` + `/opsx:archive` once shipped.
