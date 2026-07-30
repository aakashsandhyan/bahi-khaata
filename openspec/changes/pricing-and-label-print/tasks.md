## 1. Schema & migrations

- [ ] 1.1 Migration: recreate `print_job` self-contained — drop `item_type`/`item_id`, add `barcode` (text), `product_name` (text), `selling_price_paise` (int), `mrp_paise` (int, null), keep `copies`/`status`/`error`/`retry_count`/timestamps, add nullable `product_id` (CHAR(36)) back-reference. No prod rows, so recreate is safe.
- [ ] 1.2 Migration: add `product.label_printed_at` (text, nullable, ISO-8601 per convention).
- [ ] 1.3 Migration: create `product_capture` — id (CHAR(36)), name (text), mrp_paise (int, null), description (text, null), lot_id (CHAR(36), null), status (text), created_at/updated_at (text).
- [ ] 1.4 Verify all three under `ddl-auto=validate` (launch backend, confirm no SchemaManagementException).

## 2. Contracts

- [ ] 2.1 Reshape `QueuePrintJobRequest` to self-contained fields: `barcode`, `productName`, `sellingPricePaise`, `mrpPaise` (null), `copies`, optional `productId`.
- [ ] 2.2 Update `QueuePrintJobResponse`/`PrintJobStatusResponse` to drop item-type/id, reflect new fields.
- [ ] 2.3 New pricing contracts: `LotSummary`, `PriceableProduct` (with unit cost, costed flag), `LotCategory`, `PriceSuggestion`, `ShelfPricingSaveRequest` (lot, product-or-manual, category, mrp?, quantity, condition, price), `ShelfPricingSaveResult`.
- [ ] 2.4 New reconciliation contracts: `LotReconciliation` (counted vs priced/shelved per product), `WriteOffResult`.
- [ ] 2.5 New capture contracts: `CaptureRequest`, `CaptureSummary`, `ReviewQueueItem`, `ApproveCaptureRequest`.

## 3. Print executor — self-contained

- [ ] 3.1 `PrintJob`: replace item-type/id with the denormalized label fields + nullable `productId`; update `create(...)` factory.
- [ ] 3.2 `PrintExecutorService`: remove `buildLabelRequest` stub; build `PrintLabelRequest` from the job's own fields; render via `LabelTemplateService`, send via driver.
- [ ] 3.3 On successful print of a job with a `productId`, stamp that product's `label_printed_at`.
- [ ] 3.4 `PrintController` POST `/api/print-jobs`: accept the self-contained request; validate copies 1..100.
- [ ] 3.5 Bulk pairing: an endpoint/service that queues N self-contained jobs and pairs consecutive labels into `renderRow` rows; lone leftover prints as a duplicate pair (`renderLabel`). No blank stickers.
- [ ] 3.6 Update/rewrite `PrintExecutorServiceTest` for the new path (no DB lookup to render; product marked on success; failure leaves it unmarked).

## 4. Shelf pricing service

- [ ] 4.1 `ShelfPricing` service in `pricing`: `lots()` (open lots), `productsInLot(lotId)` (costed batches' products + unit cost), `categoriesForLot(lotId)` (distinct categories, empty → caller falls back to full list).
- [ ] 4.2 `suggestPrice(lotId, productId, category, customMargin?)` reusing `TargetMargins.resolve` + `Margins.priceForTargetMargin`; return none for uncosted batches (hand-priced case).
- [ ] 4.3 `saveExisting(...)`: set category + selling price (`Product.setSellingPrice`), mint BBZ via `InternalBarcodeGenerator` if none, write a `StockLedgerEntry` for the quantity onto the shelf. No new stock beyond the shelf movement.
- [ ] 4.4 `saveManual(...)`: create `Product` + `Batch.counted(product, lot, condition, quantity, mrp, estimate)`, then the same price/barcode/shelf-move path; uncosted batch → price required from caller (no suggestion).
- [ ] 4.5 Guard: existing product with a BBZ keeps it (no re-mint).
- [ ] 4.6 `ShelfPricingController` `/api/pricing/shelf`: lots, products-in-lot, categories-for-lot, suggest, save-existing, save-manual.
- [ ] 4.7 Tests: costed → suggestion; uncosted → no suggestion + hand price; save sets price/mints BBZ/ledger-moves; manifested-missing product absent from priceable.

## 5. Lot reconciliation write-off

- [ ] 5.1 `LotReconciliation` service: compute per-product phantom = counted − (priced & shelved) for a lot.
- [ ] 5.2 `writeOff(lotId)`: one append-only negative `StockLedgerEntry` for the phantom total, recorded as shrinkage/loss on the lot; no existing row edited; no-op when phantom is zero.
- [ ] 5.3 Endpoint `/api/pricing/lots/{lotId}/reconcile` (preview) and `.../write-off` (apply).
- [ ] 5.4 Tests: phantom computed correctly; write-off nets stock to physical; zero-phantom → no movement; append-only preserved.

## 6. Mobile capture & review queue

- [ ] 6.1 `ProductCapture` entity + repository (pending/approved/rejected, oldest-first query).
- [ ] 6.2 `CaptureService`: create (pricing-free), list pending, reject.
- [ ] 6.3 Approve path: reuse `ShelfPricing.save*` with reviewer-supplied lot/category/price; mark capture approved. A capture reaches the shelf only via approve.
- [ ] 6.4 `CaptureController` `/api/capture` (create — LAN, no auth) and review endpoints `/api/pricing/review-queue` (list, approve, reject).
- [ ] 6.5 Tests: capture carries no price; pending not on shelf; approve creates the same shelf product as a workbench save; reject creates nothing.

## 7. Product catalog — label-printed marker

- [ ] 7.1 `Product`: add `labelPrintedAt` (Instant, converter+text), `markLabelPrinted(at)`, `isLabelPrinted()`.
- [ ] 7.2 `ProductCatalog`: query for shelf products awaiting a label (on shelf, `label_printed_at` null) for the bulk screen.
- [ ] 7.3 Tests: unlabelled after price-without-print; marked once a label prints; reprint keeps marker set.

## 8. Frontend — pricing workbench

- [ ] 8.1 New `Pricing` workbench screen: lot picker (open lots, changeable), two add paths — pick-existing (from products-in-lot) and manual-create (nudge: only for never-counted stock).
- [ ] 8.2 Category dropdown from `categoriesForLot` (fallback full list); suggested price on category select; hand-price field when uncosted; optional MRP; quantity; condition.
- [ ] 8.3 Save → success → "print label?" prompt → queue self-contained job. Decline leaves it for bulk.
- [ ] 8.4 Lot reconciliation panel: show phantom, confirm write-off.

## 9. Frontend — bulk print & mobile

- [ ] 9.1 Bulk-print screen: list shelf products awaiting a label; multi-select; bulk queue.
- [ ] 9.2 Mobile capture screen (phone layout, LAN route): name, optional MRP/description, optional lot; submit → confirmation.
- [ ] 9.3 Review-queue screen (desktop): pending captures oldest-first; open pre-filled in the workbench; approve/reject.
- [ ] 9.4 Retire the receiving-screen `PrintModal` (cost/lot label); remove its trigger from `Receiving.tsx`.

## 10. Verify & wire-up

- [ ] 10.1 Full backend test suite green (bar the known pre-existing receiving-rewrite failures); ArchUnit green.
- [ ] 10.2 Build release, deploy to the shop machine, price a real product end-to-end, print its label, confirm the label matches the locked design.
- [ ] 10.3 Bulk-print a run of odd N; confirm ceil(N/2) rows and no blank sticker.
- [ ] 10.4 Mobile-capture from a phone on the LAN → appears in review queue → approve → on shelf → bulk-printable.
