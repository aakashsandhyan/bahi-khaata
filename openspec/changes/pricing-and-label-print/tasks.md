## 1. Schema & migrations

- [x] 1.1 Migration: recreate `print_job` self-contained — drop `item_type`/`item_id`, add `barcode` (text), `product_name` (text), `selling_price_paise` (int), `mrp_paise` (int, null), keep `copies`/`status`/`error`/`retry_count`/timestamps, add nullable `product_id` (CHAR(36)) back-reference. No prod rows, so recreate is safe.
- [x] 1.2 Migration: add `product.label_printed_at` (text, nullable, ISO-8601 per convention).
- [x] 1.3 Migration: create `product_capture` — id (CHAR(36)), name (text), mrp_paise (int, null), description (text, null), lot_id (CHAR(36), null), status (text), created_at/updated_at (text).
- [x] 1.4 Verify all three under `ddl-auto=validate` (launch backend, confirm no SchemaManagementException).

## 2. Contracts

- [x] 2.1 Reshape `QueuePrintJobRequest` to self-contained fields: `barcode`, `productName`, `sellingPricePaise`, `mrpPaise` (null), `copies`, optional `productId`.
- [x] 2.2 Update `QueuePrintJobResponse`/`PrintJobStatusResponse` to drop item-type/id, reflect new fields.
- [x] 2.3 New pricing contracts: `LotSummary`, `PriceableProduct` (with unit cost, costed flag), `LotCategory`, `PriceSuggestion`, `ShelfPricingSaveRequest` (lot, product-or-manual, category, mrp?, quantity, condition, price), `ShelfPricingSaveResult`.
- [x] 2.4 New reconciliation contracts: `LotReconciliation` (counted vs priced/shelved per product), `WriteOffResult`.
- [x] 2.5 New capture contracts: `CaptureRequest`, `CaptureSummary`, `ReviewQueueItem`, `ApproveCaptureRequest`.

## 3. Print executor — self-contained

- [x] 3.1 `PrintJob`: replace item-type/id with the denormalized label fields + nullable `productId`; update `create(...)` factory.
- [x] 3.2 `PrintExecutorService`: remove `buildLabelRequest` stub; build `PrintLabelRequest` from the job's own fields; render via `LabelTemplateService`, send via driver.
- [x] 3.3 On successful print of a job with a `productId`, stamp that product's `label_printed_at`.
- [x] 3.4 `PrintController` POST `/api/print-jobs`: accept the self-contained request; validate copies 1..100.
- [x] 3.5 Bulk pairing: an endpoint/service that queues N self-contained jobs and pairs consecutive labels into `renderRow` rows; lone leftover prints as a duplicate pair (`renderLabel`). No blank stickers.
- [x] 3.6 Update/rewrite `PrintExecutorServiceTest` for the new path (no DB lookup to render; product marked on success; failure leaves it unmarked).

## 4. Shelf pricing service

- [x] 4.1 `ShelfPricing` service in `pricing`: `lots()` (open lots), `resolveScanned(code)` (LSN/ASIN via barcode resolver → already-counted product + its batch in the lot + unit cost), `categoriesForLot(lotId)` (distinct categories, empty → caller falls back to full list).
- [x] 4.2 `suggestPrice(unitCost, category, customMargin?)` reusing `TargetMargins.resolve` + `Margins.priceForTargetMargin`; only for costed stock (uncosted → no suggestion, hand-priced).
- [x] 4.3 `saveExisting(...)`: scanned already-counted product — set category + selling price (`Product.setSellingPrice`), confirm the batch MRP (non-estimate), mint BBZ if none. **No** ledger movement (already received at counting).
- [x] 4.4 Expose an inventory entry point (`GoodsInCounting.receiveManual(lot, product, condition, qty, mrp, at)` reusing `addToBatch`) that creates the batch + writes the receipt; `saveManual(...)` creates the `Product`, calls it, then sets category/price/confirmed-MRP/BBZ. Uncosted batch → price required from caller.
- [x] 4.5 Guard: existing product with a BBZ keeps it (no re-mint). MRP entered at pricing is recorded confirmed.
- [x] 4.6 `ShelfPricingController` `/api/pricing/shelf`: lots, products-in-lot, categories-for-lot, suggest, save-existing, save-manual.
- [x] 4.7 Tests: costed → suggestion; uncosted → no suggestion + hand price; save sets price/mints BBZ/ledger-moves; manifested-missing product absent from priceable.

## 5. Lot reconciliation write-off

- [x] 5.1 `LotReconciliation` service: compute per-product phantom = counted − (priced & shelved) for a lot.
- [x] 5.2 `writeOff(lotId)`: one append-only negative `StockLedgerEntry` for the phantom total, recorded as shrinkage/loss on the lot; no existing row edited; no-op when phantom is zero.
- [x] 5.3 Endpoint `/api/pricing/lots/{lotId}/reconcile` (preview) and `.../write-off` (apply).
- [x] 5.4 Tests: phantom computed correctly; write-off nets stock to physical; zero-phantom → no movement; append-only preserved.

## 6. Mobile capture & review queue

- [x] 6.1 `ProductCapture` entity + repository (pending/approved/rejected, oldest-first query).
- [x] 6.2 `CaptureService`: create (pricing-free), list pending, reject.
- [x] 6.3 Approve path: reuse `ShelfPricing.save*` with reviewer-supplied lot/category/price; mark capture approved. A capture reaches the shelf only via approve.
- [x] 6.4 `CaptureController` `/api/capture` (create — LAN, no auth) and review endpoints `/api/pricing/review-queue` (list, approve, reject).
- [x] 6.5 Tests: capture carries no price; pending not on shelf; approve creates the same shelf product as a workbench save; reject creates nothing.

## 7. Product catalog — label-printed marker

- [x] 7.1 `Product`: add `labelPrintedAt` (Instant, converter+text), `markLabelPrinted(at)`, `isLabelPrinted()`.
- [x] 7.2 `ProductCatalog`: query for shelf products awaiting a label (on shelf, `label_printed_at` null) for the bulk screen.
- [x] 7.3 Tests: unlabelled after price-without-print; marked once a label prints; reprint keeps marker set.

## 8. Frontend — pricing workbench

- [x] 8.1 New `Pricing` workbench screen: lot picker (open lots, changeable), two add paths — pick-existing (from products-in-lot) and manual-create (nudge: only for never-counted stock).
- [x] 8.2 Category dropdown from `categoriesForLot` (fallback full list); suggested price on category select; hand-price field when uncosted; optional MRP; quantity; condition.
- [x] 8.3 Save → success → "print label?" prompt → queue self-contained job. Decline leaves it for bulk.
- [x] 8.4 Lot reconciliation panel: show phantom, confirm write-off.

## 9. Frontend — bulk print & mobile

- [x] 9.1 Bulk-print screen: list shelf products awaiting a label; multi-select; bulk queue.
- [x] 9.2 Mobile capture screen (phone layout, LAN route): name, optional MRP/description, optional lot; submit → confirmation.
- [x] 9.3 Review-queue screen (desktop): pending captures oldest-first; open pre-filled in the workbench; approve/reject.
- [x] 9.4 Retire the receiving-screen `PrintModal` (cost/lot label); remove its trigger from `Receiving.tsx`.

## 10. Verify & wire-up

- [ ] 10.1 Full backend test suite green (bar the known pre-existing receiving-rewrite failures); ArchUnit green.
- [ ] 10.2 Build release, deploy to the shop machine, price a real product end-to-end, print its label, confirm the label matches the locked design.
- [ ] 10.3 Bulk-print a run of odd N; confirm ceil(N/2) rows and no blank sticker.
- [ ] 10.4 Mobile-capture from a phone on the LAN → appears in review queue → approve → on shelf → bulk-printable.
