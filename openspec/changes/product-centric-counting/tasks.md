# Tasks — product-centric-counting

## 1. Contracts
- [ ] 1.1 `ProductBoxLine(UUID lineId, String boxTracking, long outstanding)`.
- [ ] 1.2 `ProductLotLines(UUID productId, String productName, UUID lotId, List<ProductBoxLine> lines)`.
- [ ] 1.3 `BoxCountEntry(UUID lineId, long quantity, long outstandingSeen)`.
- [ ] 1.4 `ProductCountRequest(UUID productId, UUID lotId, StockCondition condition, Long mrpPaise, boolean mrpIsEstimate, List<BoxCountEntry> entries)`.
- [ ] 1.5 `RejectedEntry(UUID lineId, String boxTracking, long nowOutstanding)` and `ProductCountResult(int linesCounted, long unitsCounted, List<RejectedEntry> rejected)`.

## 2. Backend — catalog lot filter (modifies product-catalog)
- [ ] 2.1 Add a paged, lot-scoped query to `ProductCatalogRepository`: products with an expected line in a given lot, combined with the name filter (and reused for on-paper/found/all). Restrict via `EXISTS (SELECT 1 FROM ExpectedLine el WHERE el.product = p AND el.lot.id = :lotId)`.
- [ ] 2.2 Scope the expected/counted totals to the lot: an `expectedTotalsForLot(ids, lotId)` bulk `GROUP BY` that filters `el.lot.id = :lotId`.
- [ ] 2.3 Scope found/on-paper to the lot when set: found = a batch exists for the product **in that lot** (`BatchRepository.findByLotIdAndProductId`), not a global batch/code check.
- [ ] 2.4 `ProductCatalog.browse` gains an optional `lot`; when present it uses the lot-scoped queries and totals, else the existing global path. Keep the category/status/name combination working.
- [ ] 2.5 `CatalogController` `GET /api/catalog` gains an optional `lot` request param (blank = all lots), passed through.

## 3. Backend — product-centric counting
- [ ] 3.1 `ProductCounting` service in the inventory package (deps: `ExpectedLineRepository`, `LotRepository`, `GoodsInCounting`, `BarcodeRepository` for code→product resolution).
- [ ] 3.2 `linesFor(UUID lotId, UUID productId)` → `ProductLotLines`: require the lot open; return each `ExpectedLine` for the product in the lot whose outstanding (`quantityExpected - quantityCounted`) > 0, with box tracking and outstanding. A closed lot returns nothing.
- [ ] 3.3 `count(ProductCountRequest)` → `ProductCountResult`, `@Transactional`, one unit of work:
  - for each entry, re-read the line's current outstanding; if it differs from `outstandingSeen`, add to `rejected` (with `nowOutstanding`) and skip;
  - else cap `quantity` at the current outstanding (min 0) and call `GoodsInCounting.countExpected(lineId, condition, quantity, mrp, mrpIsEstimate, null, null, at)`;
  - the whole set commits together; return counts + rejected list.
- [ ] 3.4 Validate the request: lot open, product resolvable, every `lineId` belongs to `(productId, lotId)` — reject a line that does not, so the endpoint cannot be driven to count someone else's line.
- [ ] 3.5 `ProductCountingController`: `GET /api/product-counting/lots/{lotId}/products/{productId}/lines` and `POST /api/product-counting/count`.

## 4. Backend — tests
- [ ] 4.1 Catalog lot filter: a product in two lots shows only the chosen lot's expected/counted; no-lot spans both; found/on-paper reflects the lot.
- [ ] 4.2 `linesFor` returns a product's outstanding box-lines for an open lot; empty for a closed lot; fully-counted lines drop out.
- [ ] 4.3 `count` records each accepted entry via the same path as box counting (batch + ledger identical); one transaction.
- [ ] 4.4 Cap: an entry above outstanding is capped, never over-counts.
- [ ] 4.5 Concurrency: an entry whose `outstandingSeen` no longer matches (simulate a prior count) is rejected with the new outstanding; other entries in the same submission still commit.
- [ ] 4.6 Guard: a `lineId` not belonging to `(productId, lotId)` is refused.
- [ ] 4.7 Receiving untouched: a product-centric count leaves the box's `BoxReceipt` state unchanged.
- [ ] 4.8 ArchUnit: new code respects `inventory → catalog`, no reverse dependency.

## 5. Frontend — types + api
- [ ] 5.1 Add the new contract types to `types.ts`; add `lot` to the catalog `CatalogEntry` usage (units are already there — now lot-scoped when filtered).
- [ ] 5.2 `api.catalog.browse` gains an optional `lot` argument, threaded into the query string.
- [ ] 5.3 `productCounting.lines(lotId, productId)` and `productCounting.count(body)` clients.

## 6. Frontend — lot filter + counting grid
- [ ] 6.1 Add a **lot** filter to `Catalog.tsx`: options from the existing deliveries read (`unpacking.deliveries()`), shown as supplier · category · date so same-category lots are distinct; selecting one scopes the list and its counts.
- [ ] 6.2 Wire the catalogue's **Count** action (currently a stub): enabled only when a lot is chosen; opens the product-centric grid for `(lot, product)`.
- [ ] 6.3 Build the grid: one row per box (tracking + outstanding + a quantity field, capped at outstanding), a single condition picker and MRP field, one submit. Reuse the submit-once guard and the quantity input from unpacking.
- [ ] 6.4 On submit, send each box's `(lineId, quantity, outstandingSeen)`; on result, show any **rejected** entries with their new outstanding for re-entry (a normal path, not an error), and confirm the accepted count.
- [ ] 6.5 CSS for the grid, reusing design tokens; `pcc-`/`grid-` prefixed classes, no collisions.

## 7. Verify
- [ ] 7.1 `./gradlew :contracts:build :backend:test :architecture:test` — new tests green (note: the wider suite is red from the in-progress receiving rewrite; confirm this change's tests and ArchUnit pass).
- [ ] 7.2 `npx tsc --noEmit` and `npx vite build` clean.
- [ ] 7.3 Manual: pick a lot in the catalogue → counts scope to it → select a product → Count opens the grid → enter per-box quantities → submit → accepted counts land, a stale line comes back for re-entry; confirm box-centric counting still works and receiving state is unchanged.
- [ ] 7.4 `openspec validate product-centric-counting` passes; ready to sync + archive after implementation.
