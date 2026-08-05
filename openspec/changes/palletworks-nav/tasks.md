## 1. Settings + hash landing

- [ ] 1.1 `Settings.tsx` tab shell (Label printer / Receipt printer / Bill), per-tab conditional mount, hosting the three admin components unchanged; nav gains Settings, loses the three admin entries
- [ ] 1.2 `App.tsx` initial-view hash resolution on any viewport (`#till`, `#capture`), initial-only; Till entry removed from `Sidebar.tsx`
- [ ] 1.3 Rewrite smokes: 01-till via `#till`; 11/15/16 navigate Settings tabs; tsc + build green

## 2. Category endpoint (only new backend)

- [ ] 2.1 `PATCH /api/products/{id}/category` → `Product.setCategory`; unknown product/category refused; backend test both paths
- [ ] 2.2 `./gradlew :backend:test` green

## 3. Inventory scope + item-detail additions

- [ ] 3.1 `Inventory.tsx` scope seg (On floor / On paper / All): On paper + All ride `catalog.browse` with catalog columns (expected/counted, department chips), no fabricated stock cells; totals footer On floor only; search + department in every scope; on-paper row opens item detail
- [ ] 3.2 `ItemDetail.tsx`: category editor (new endpoint); Count action with open-deliveries picker (preselect single owing lot) feeding `ProductCountPane` unchanged; opens for on-paper products with honest empty sections
- [ ] 3.3 tsc + build green; rewrite 08-catalog as on-paper-scope smoke (search, gap fact, open detail, count entry)

## 4. Delete Catalog

- [ ] 4.1 Remove `Catalog.tsx`, nav entry, `catalog.detail` client fn (keep `catalog.browse`); Sidebar/App/View cleanup
- [ ] 4.2 Full suite green once (fast gate)

## 5. Verify & finish (main session)

- [ ] 5.1 Suite green twice + backend suite green (independent runs)
- [ ] 5.2 Visual walk desktop + phone viewport: Settings tabs, scope seg, on-paper detail, #till landing
- [ ] 5.3 Prod-copy spot check (2,201 products through catalog.browse scopes; timing noted)
- [ ] 5.4 Scenario→named-test map complete or unproven listed; checkboxes; PR-ready
