## Why

The shop cannot answer "what is on the floor and what is it worth" without mental arithmetic across Catalog, Prep, and Pricing: there is no stock-centric view, no record of where goods physically sit, no journal of price changes, and no single place to read one product's full story (movements, batches, prices). Phase 3 of the Palletworks rebuild (scope approved in session 2026-08-05) adds the Inventory table, a per-product Item detail view, physical bin locations, and a price-change journal.

## What Changes

- New **Inventory** screen (Operations group, after Review): a dense stock table from one aggregate query — product, condition, lot, bin, on-hand quantity (ledger sum), cost basis, price, margin, age since first receipt — with condition/bin/lot/aging/search filters, filtered-set totals (units, cost, retail value), and client-side CSV export.
- New **Item detail** view opened from an Inventory row (and linked from Catalog's panel): header with condition and barcodes, KPI cells (cost basis, price, margin, sold/received), movement log from the stock ledger, price history from the new journal, per-batch list with editable bin, and an actions rail (reprice via the existing shelf-pricing endpoint, queue a label reprint).
- **V45 migration**: nullable `bin` TEXT on `batch`; append-only `price_history` table (product, old/new price, changed-at, operator) guarded against update/delete like the ledger.
- Shelf-pricing write path journals every price set and reprice from now on.
- **PricingWorkbench** gains an optional bin field written to the batch on save.
- e2e seed gains bins and a seeded price change; three new smokes (suite ~24). No photos, no quarantine, no label-template changes, no floor/back-room split.

## Capabilities

### New Capabilities
- `inventory-view`: the Inventory table and its aggregate API — columns, filters, totals, CSV export, age definition.
- `item-detail`: the per-product view — KPIs, movement log, price history, batch list with bin editing, actions.
- `price-history`: the append-only price journal — write on every price change, immutability, read API.
- `bin-locations`: batch-level bin field — assignment at pricing and item detail, display and filtering in inventory.

### Modified Capabilities
- `dashboard-shell`: Operations group gains an Inventory entry (nav list requirement changes).
- `dashboard-smoke-tests`: per-screen coverage extends to the new screens; seed gains bins and a price-change row.
- `shelf-pricing`: pricing a product SHALL journal the price change and MAY set the batch bin (requirement-level change to the save behavior).

## Impact

- Backend: V45 migration; `inventory` package (read-only aggregate + detail endpoints, bin write); shelf-pricing service touched at its save path (journal write). Existing tests must stay green; new tests for journal writes, aggregate math, age computation.
- Frontend: `Inventory.tsx`, `ItemDetail.tsx` (new), `Sidebar.tsx`/`App.tsx` (nav + view), `PricingWorkbench.tsx` (bin field), `Catalog.tsx` (detail link), `api.ts`/`types.ts`, `styles.css`.
- Contracts: inventory row/detail/price-history records.
- Risk: touching the shelf-pricing write path — the money path; mitigated by keeping the journal write additive (no behavior change to price setting itself) and by the existing pricing smokes.
