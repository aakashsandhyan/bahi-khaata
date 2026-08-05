## Context

Phase 1 (palletworks-foundation) gave the app one visual language, a grouped sidebar with state-based view switching (`App.tsx` holds `view`; `Sidebar` takes `onNavigate`), and a Playwright smoke suite. Phase 2 (palletworks-dashboard) added the read-only aggregate pattern: a `dashboard` package whose `DashboardRepository` runs single-pass native `JdbcTemplate` aggregates over tables other packages own, with response records in the `contracts` module and a hand-mirror in `types.ts`. Phase 3 adds the stock-centric view the shop still lacks: an **Inventory** table, a per-product **Item detail** view, physical **bin** locations on batches, and an append-only **price-change journal**.

The moving parts already exist and are honest: `stock_ledger` (V6) is append-only and the source of truth for on-hand quantity, guarded by no-update/no-delete triggers; `batch` (V5, rebuilt V25) carries condition, issue type, allocated cost, and MRP; every price write funnels through one choke point — `ProductPricing.setSellingPrice(UUID, Money, boolean)` — which is the only place the entity setter `Product.setSellingPrice` is called. Timestamps are ISO-8601 UTC text, sortable as strings; the shop runs IST.

## Goals / Non-Goals

**Goals:**
- One aggregate endpoint answering "what is on the floor, what is it worth, how old is it" in a single round trip, fast on shop hardware.
- A per-product detail view stitching batches, ledger movements, and price history into one story.
- A durable, immutable record of every price change, written wherever a price is set — one hook, not five.
- Physical bin locations captured at pricing and editable on the item.
- Deterministic e2e: seeded bins and a seeded price change let the new smokes assert exact rows; existing specs keep passing.

**Non-Goals:**
- No photos, no quarantine, no label-template change, no floor/back-room split, no charts.
- No second price-write path: item-detail reprice calls the existing shelf-pricing endpoint.
- No operator threading through the price choke point (nullable column, deferred).

## Decisions

**D1 — Inventory row granularity is one row per product × condition, aggregated across lots.** A DAMAGED unit prices and values differently from a GOOD one, so condition must split the row; lot must not, or a product spread over three deliveries becomes three near-identical rows. The row shows its lot when a single lot backs it, else "N lots"; the per-batch breakdown (with lot and bin) lives in the detail view. *Rejected:* per-batch rows (mockup is per-SKU; explodes a common product into a dozen lines); per-product-only (hides condition, so cost/price/value silently blend sellable and damaged stock).

**D2 — One endpoint `GET /api/inventory`, a single-pass native aggregate in a read-only `inventory`-package repository, mirroring `DashboardRepository`.** On-hand = `SUM(sl.quantity)` grouped by `product_id, b.condition` over `stock_ledger sl JOIN batch b JOIN product p`; cost from `b.allocated_unit_cost_paise`, price from `p.selling_price_paise`, margin derived, age from `MIN(sl.effective_at)` where `movement_type='PURCHASE_RECEIPT'`, lot count and bin folded in the same scan. `JdbcTemplate` not JPA for the reason phase-2 D3 already set: the query spans tables no single entity owns and must not load a row per product over a ledger that only grows. *Rejected:* per-widget/per-column endpoints; loading batches and summing in Java (N+1 over the ledger).

**D3 — Detail endpoint `GET /api/inventory/product/{id}` composes three reads: batches (with bin), ledger movements, price history.** The list and the detail have different shapes and cardinalities; forcing one query to serve both would denormalise the list or starve the detail. *Rejected:* deriving the detail by client-side filtering of the list payload (the list is aggregated, so it has already discarded per-movement and per-batch rows).

**D4 — Age is whole days since the product's first `PURCHASE_RECEIPT` effective time, computed as a UTC-day difference.** Age buckets are coarse (a "45-day-old" tag), so an IST-vs-UTC boundary error of at most one day is immaterial — this does not warrant the IST calendar-window ceremony phase-2 needed for a revenue tile that had to land a sale in the right day. *Rejected:* IST-precise day arithmetic (ceremony with no visible payoff at this resolution).

**D5 — The price journal is written in `ProductPricing.setSellingPrice(UUID, Money, boolean)` — the single choke point every price path funnels through.** Read the old price before the set, then append a `price_history` row (`old_price_paise` NULL on first set). Confirmed callers all reach the entity setter only here: `ProductController` catalog edit, `PricingWorkbench` reprice (×2), `ShelfPricing.saveExisting`/`saveManual` (×2). One hook covers all five. *Rejected:* journaling in each caller (five sites, guaranteed drift the day a sixth is added); an `AFTER UPDATE` DB trigger on `product` (cannot see the operator, and buries a business event in schema where no reviewer looks).

**D6 — `operator_name` is written NULL for now.** The choke-point signature carries no operator, and threading one through all five callers is out of this change's scope; the column is nullable and forward-compatible, so the journal is complete and a later change can backfill the field without a rewrite. *Rejected:* widening the signature across five callers now (scope creep onto the money path).

**D7 — V45 adds `batch.bin` via `ALTER TABLE ADD COLUMN bin TEXT` (nullable, no default) and creates `price_history` with the V6 immutability trigger pair.** A nullable no-default column add is a cheap metadata-only operation — no table rebuild, unlike V25, which rebuilt `batch` only because it was changing constraints (the identity index), which `ADD COLUMN` cannot express. `price_history` (`id`, `product_id`, `old_price_paise` NULL, `new_price_paise`, `operator_name` NULL, `created_at`) gets `no_update`/`no_delete` triggers copied from `stock_ledger`; being immutable, it has no `updated_at`. *Rejected:* a rebuild for the bin column (needless); a `bin` lookup table (over-modelled — a bin is one free-text tag on the batch).

**D8 — Bin write is `PUT /api/inventory/batch/{id}/bin`, the only new write besides the journal.** It sets `batch.bin`, trimming blank to NULL. `PricingWorkbench` sets bin on its existing save (a field added to the request and written to the batch there, no new round trip); item detail edits it per batch through this endpoint. *Rejected:* a bulk bin-assign screen (not asked for); folding bin into the shelf-pricing request for item-detail edits too (the item-detail bin edit is not a repricing).

**D9 — Item detail is a view carrying a product id; `App` gains `detailProductId` alongside `view` and an `onOpenItem(id)` callback.** Phase-2's `onNavigate` is param-less; opening an item needs to carry which one, from both the Inventory row and the Catalog panel, so the callback extends the same state-switch mechanism rather than adding a router. *Rejected:* a router (phase 1 chose state switching on purpose); detail as Inventory-internal state (Catalog must open it too, so it cannot live inside Inventory).

**D10 — CSV export is client-side from the loaded rows.** The table already holds every column in memory; a server export path would duplicate the aggregate query for no gain. *Rejected:* a `GET /api/inventory.csv` endpoint (second copy of the same query to keep in sync).

**D11 — Inventory is added to the Operations group after Review; e2e seed gains bins on at least one batch and one `price_history` row; three new smokes take the suite to ~24.** Smokes: inventory list renders with filtered-set totals; item detail shows all three sections for a seeded product; a bin edit persists across reload. Every spec scenario maps to a named automated test or is explicitly returned unproven at apply time — a scenario with no passing test is not "done". *Rejected:* asserting totals against live-computed values (seed fixed constants so the assertion is exact).

## Risks / Trade-offs

- [The journal write sits on the money path — a bug there could fail a price set] → the write is additive and after the guarded set; the append cannot alter the price, and the existing pricing smokes plus a new journal-write test guard the path.
- [Aggregate scan over the growing ledger slows the payload] → single indexed pass (`idx_ledger_product_effective`, `idx_batch_product_lot`), no N+1; timing observed in the smoke run.
- [`ADD COLUMN` on `batch` misjudged as needing a rebuild] → confirmed nullable no-default is metadata-only in SQLite; V25's rebuild was constraint-driven, not column-driven, and does not apply here.
- [`operator_name` NULL misread later as "no operator known" vs "not captured yet"] → documented as deferred capture, not a lost value; a future change backfills the field, not the history.
- [Bin free-text drifts (e.g. "A1" vs "a-1")] → accepted; a bin is an operator's own shorthand, and normalising it now would model a taxonomy the shop has not defined.

## Migration Plan

1. **V45**: `ALTER TABLE batch ADD COLUMN bin TEXT`; `CREATE TABLE price_history (...)` + `no_update`/`no_delete` triggers. `Batch` entity gains a `bin` field; new `PriceHistory` entity/repository (append-only, insert only).
2. Backend `inventory` read package (aggregate + detail `JdbcTemplate` repo, controller, bin `PUT`); `ProductPricing.setSellingPrice` journals; `InventoryRow`/`InventoryDetail`/`PriceChange` records in `contracts`. Existing tests stay green; new tests cover aggregate math, age, and the journal write from every caller.
3. Frontend: `Inventory.tsx`, `ItemDetail.tsx` (new); `App.tsx`/`Sidebar.tsx` (view + `detailProductId` + `onOpenItem`); `PricingWorkbench.tsx` (bin field); `Catalog.tsx` (detail link); `api.ts`/`types.ts`/`styles.css`.
4. e2e seed bins + one `price_history` row; three new specs. `npm run e2e` green is the merge gate.
5. Deploy = existing jar-swap; V45 runs on boot. Rollback = previous jar (the additive column and new table are inert to it). No printer paths touched.

## Open Questions

- Whether item detail should offer an in-place bin edit for **all** conditions or only sellable ones — ships editable for all (a NEEDS_WORK unit still occupies a physical bin); revisit if operators want prep stock hidden. Non-blocking.
