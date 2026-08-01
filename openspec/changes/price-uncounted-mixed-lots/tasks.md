## 1. Lot picker includes open no-count lots

- [ ] 1.1 Widen `ShelfPricing.lots()` to list a lot when it is OPEN **or** it has counted-but-unpriced stock: fetch `LotRepository.findByState(OPEN)` and `lots.findAllById(batches.lotIdsWithUnpricedStock())`, merge de-duplicated by id, sort by `receivedOn` desc, map to `ShelfLot`. (Add `findByState` to `LotRepository` if not present.)

## 2. Category fallback for a batch-less lot

- [ ] 2.1 Change `ShelfPricing.categoriesForLot()` to return the full list of category codes (from the category lookup table — reuse whatever source the catalog/category endpoints use) when the lot has no batches; keep batch-derived categories when it does. This implements the fallback the `shelf-pricing` spec already mandates.

## 3. Tests

- [ ] 3.1 `ShelfPricing.lots()`: an open lot with no batches IS listed; a closed lot whose counted stock is all priced is NOT listed; a lot with counted-but-unpriced stock is still listed; no duplicates when a lot is both open and has unpriced stock.
- [ ] 3.2 `categoriesForLot()`: a batch-less lot returns the full category list; a lot with batches returns only its batch categories.
- [ ] 3.3 End-to-end: select an open, uncounted lot and hand-add a product (`saveManual`) — assert a batch + stock ledger receipt are created and the product is priced (the no-count path now reachable).

## 4. Verification

- [ ] 4.1 Backend build + tests green (`./gradlew :backend:test`).
- [ ] 4.2 Dashboard builds green (`npm run build`) — no frontend change expected; confirm the empty-lot picker + hand-add flow works against a running backend (add lot → Pricing lists it → Add by hand prices it).
