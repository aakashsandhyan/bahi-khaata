# Tasks — product-catalog

## 1. Contracts
- [x] 1.1 Add `CatalogStatus` enum (`FOUND`, `ON_PAPER`) in `contracts`.
- [x] 1.2 Add `CatalogEntry(UUID productId, String name, String categoryCode, CatalogStatus status, boolean priced)` record.
- [x] 1.3 Add a `ProductCode(String code, Origin origin)` record for the detail's code list (or confirm an existing barcode summary fits and reuse it).

## 2. Backend — queries (`catalog` package)
- [x] 2.1 Add to `ProductRepository` a paged, name-filtered query for **on-paper** products: `name LIKE %:q%` AND `NOT EXISTS` a batch for the product AND `NOT EXISTS` a non-`MARKETPLACE` barcode, ordered by name, taking a `Pageable`.
- [x] 2.2 Add the **found** counterpart: same name filter with `EXISTS` a batch OR `EXISTS` a physical (non-`MARKETPLACE`) barcode.
- [x] 2.3 Add the **all** variant: paged, name-filtered, no status predicate, ordered by name.
- [x] 2.4 Confirm foreign-key indexes exist on `barcode.product_id` and `batch.product_id` (they are FKs); note in the PR if an explicit index is warranted.

## 3. Backend — service + controller (`inventory` package, beside `GoodsRemediation`)
- [x] 3.1 Add `ProductCatalog` service in `inventory` (it reads `Batch` + `Barcode` + `Product`, and `inventory → catalog` is the allowed dependency direction — mirrors `GoodsRemediation`).
- [x] 3.2 `browse(String q, CatalogStatus filter | "all", Pageable)` → `List<CatalogEntry>`, choosing the repository query by filter and mapping each product to its `CatalogEntry` (status from the query it came through; `priced` from `sellingPrice != null`).
- [x] 3.3 `detail(UUID productId)` → reuse `GoodsRemediation.statesOf` (or call the same batch/product read) for name + category + per-condition quantities, plus the product's codes from `BarcodeRepository.findByProductId` mapped to `ProductCode`.
- [x] 3.4 Add `CatalogController` at `/api/catalog`: `GET /api/catalog?q=&status=on-paper|found|all&page=&size=` → paged `CatalogEntry` list; `GET /api/catalog/products/{id}` → detail (or document reuse of `/api/remediation/products/{id}/states` for the states half).
- [x] 3.5 Default `status` to `on-paper` when absent, so the default view is the gaps.

## 4. Backend — tests
- [x] 4.1 Status derivation: manifest-only product → on-paper; counted product (batch, no physical code) → found; tagged product (physical code, no batch) → found; product whose only code is `MARKETPLACE` → on-paper.
- [x] 4.2 Name filter is case-insensitive and matches a fragment; empty filter lists from the start.
- [x] 4.3 Paging returns the next products in name order beyond the first page (proves the 25-cap is gone).
- [x] 4.4 Each filter (`on-paper` / `found` / `all`) returns exactly the right set.
- [x] 4.5 Detail returns name, category, per-condition quantities, and the product's codes.
- [x] 4.6 Run the `architecture` module (ArchUnit) — confirm the new service respects `inventory → catalog` and no reverse dependency was introduced.

## 5. Frontend — types + api (`dashboard/web`)
- [ ] 5.1 Add `CatalogStatus`, `CatalogEntry`, `ProductCode` to `types.ts` (reuse `ProductStates` for the detail states).
- [ ] 5.2 Add `catalog.browse(q, status, page)` and `catalog.detail(productId)` to `api.ts`; the detail states half may reuse `remediation.states`.

## 6. Frontend — Catalog tab
- [ ] 6.1 Widen `App.tsx` `view` union with `'catalog'`, add the nav button and the render branch.
- [ ] 6.2 Build a `Catalog` component: name search box, status tabs (**On paper** default / Found / All), a scannable/typeable filter, rows showing name · category · found-or-on-paper badge · priced status.
- [ ] 6.3 Row select opens a detail panel reusing the states view and the product's codes; wire the existing **set price** and **map a code** actions.
- [ ] 6.4 Add a **Count** affordance that selects the product and signals counting intent (hand-off stub — records nothing; the product-centric-counting change attaches here).
- [ ] 6.5 Add CSS for the catalogue list/detail, matching the existing design-system tokens; avoid class-name collisions (check existing `.qty-row`-style clashes).

## 7. Verify
- [ ] 7.1 `./gradlew :contracts:build :backend:test :architecture:test` green.
- [ ] 7.2 `npx tsc --noEmit` and `npx vite build` clean.
- [ ] 7.3 Manual: open Catalog → default shows on-paper products; search by name; switch to Found; open a product → detail shows states + codes; set a price and confirm the row's priced status updates; confirm Count only hands off (records nothing).
- [ ] 7.4 `openspec validate product-catalog` still passes; ready to `openspec archive` after implementation.
