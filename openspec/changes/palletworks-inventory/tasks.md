## 1. Migration + journal (money path)

- [x] 1.1 V45 migration: `batch.bin TEXT` via `ALTER TABLE ADD COLUMN`; `price_history` table + V6-style no-UPDATE/no-DELETE triggers
- [x] 1.2 Journal write inside `ProductPricing.setSellingPrice(UUID, Money, boolean)` per D5: read old price first, NULL old on first set, skip when unchanged; no other file writes history
- [x] 1.3 Backend tests: journal row per caller path (workbench single, bulk, saveExisting, saveManual, catalog inline edit), NULL-old first set, no-op skip, trigger rejection of UPDATE/DELETE
- [x] 1.4 `./gradlew :backend:test` green; commit

## 2. Inventory + detail backend

- [x] 2.1 Contracts: inventory row, item detail (batches/movements/price history), price-history entry records
- [x] 2.2 `inventory` package: single-pass aggregate for `GET /api/inventory` (row per product × condition, net on-hand > 0 only, lot rollup, age via first PURCHASE_RECEIPT, margin derived); `GET /api/inventory/product/{id}`; `PUT /api/inventory/batch/{id}/bin` (blank → NULL)
- [x] 2.3 Backend tests: aggregate math (two-condition product = two rows, zero-stock excluded), age computation, detail composition, bin write blank→NULL
- [x] 2.4 `./gradlew :backend:test` green; commit

## 3. e2e seed

- [x] 3.1 Seed: bins on two batches (one set, one left NULL), one fixed price_history row for the priced kettle; constants mirrored in `e2e/seed.ts`
- [x] 3.2 Fresh suite boot green (existing 21 specs); commit

## 4. Frontend

- [x] 4.1 `types.ts` mirrors + `api.ts` (inventory list, product detail, bin put)
- [x] 4.2 `Inventory.tsx`: dense table, condition tags, em-dash absent bins, filter strip (condition/bin/lot/aging/search), filtered totals footer, CSV export from loaded rows
- [x] 4.3 `ItemDetail.tsx`: KPI cells, movement log, price history (newest-first, first-set marker), batch list with inline bin edit, actions rail (reprice via existing shelf-pricing endpoint, queue reprint); loading/error states per house pattern
- [x] 4.4 `App.tsx` `detailProductId` + `onOpenItem(id)` per D9; `Sidebar.tsx` Inventory after Review; `Catalog.tsx` panel link to item detail; `PricingWorkbench.tsx` optional bin field on save
- [x] 4.5 tsc + vite build green; commit

## 5. Smoke tests

- [x] 5.1 `17-inventory.spec.ts`: loads via sidebar; seeded rows with condition tags; bin filter narrows totals; em-dash on binless row; row click opens item detail
- [x] 5.2 `18-item-detail.spec.ts`: KPIs render; movement log shows seeded receipt; price history shows seeded row with old→new; bin edit round-trips; reprice writes a new history row (asserts journal through the UI)
- [x] 5.3 `19-pricing-bin.spec.ts`: pricing save with bin lands on batch; inventory reflects it
- [x] 5.4 Suite green twice consecutively; commit

## 5b. Manual receiving-finished (prod-copy finding)

- [x] 5b.1 `PUT /api/lots/{id}/receiving-complete` on LotController: open, not-yet-complete lots only; sets the same flag the automatic path sets; no state/stock change
- [x] 5b.2 Lots screen: "Receiving finished" action on lots still receiving; backend test + smoke (`10-lots` extended or new spec) prove flag set + idempotent refusal

## 6. Verify & finish

- [x] 6.1 Full e2e + backend suites green; every spec scenario maps to a named automated test or is listed unproven in the verify report
- [x] 6.2 Visual walk-through desktop + 420px with seeded data; totals spot-checked against seed math
- [x] 6.3 Update checkboxes; ready for review/PR
