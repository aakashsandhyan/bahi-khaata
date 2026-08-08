## Why

A lot's per-unit cost is derived one way today: the amount paid is apportioned across its lines by weight (`RELATIVE_MRP`), or the manifest rate `(amountPaid+freight) ÷ Σ(stated_value×qty)` pins each line. But the shop buys lots on genuinely different commercial terms, and cost should follow the deal:

- a flat per-piece rate for the whole lot,
- a fixed **percentage of MRP** (or of the marketplace/Amazon selling price, "ASP"),
- an **MRP rate card** — cost banded by the item's MRP,
- a **multiplier** on a base cost.

None of these can be expressed now, so cost is forced through apportionment even when the real basis is "we pay 30% of MRP". This change lets a **lot declare its cost basis** — a strategy anchored to MRP or ASP — and derives each product's cost from it, with the amount paid kept only as a cross-check. It builds directly on the lot being a first-class editable entity (category + `PUT /api/lots/{lotId}` guarded by `LotEditPolicy`, just shipped).

## What Changes

- A **lot carries a cost basis**: `{ strategy, anchor, params }`, set at creation and edited on the existing `PUT /api/lots/{lotId}` — guarded by `LotEditPolicy.requireEditable`, so once any stock from the lot is consumed the basis is frozen (409): changing it would rewrite the cost of goods already sold.
- Four strategies (all built now — the shop decided this once):
  1. **FLAT_PER_UNIT** — one per-unit cost for every unit in the lot.
  2. **PERCENT_OF_ANCHOR** — cost = percent × anchor (MRP or ASP).
  3. **MRP_RATE_RANGE** — a per-lot rate card: MRP bands → a cost each; a product's cost is the band its MRP falls in.
  4. **MULTIPLIER** — cost = multiplier × base, base being an entered per-unit cost, the MRP/ASP anchor, or the manifest stated value.
- The **anchor is explicit per lot**: MRP (the batch's recorded MRP) or ASP (`product.onlinePrice`, observed from a manifest).
- **Resolution timing**: a batch's cost pins when its anchor is known. FLAT_PER_UNIT and entered-base MULTIPLIER pin at creation; MRP-anchored strategies pin at counting (when the MRP is recorded); ASP-anchored at import. Until its anchor exists a batch stays uncosted (`isCosted()=false` → zero COGS, still sellable, priceable later) — unchanged from today.
- **Amount paid becomes a cross-check** for cost-basis lots: the sum of derived line costs is compared to the amount paid and a mismatch is flagged for reporting — it no longer drives apportionment. (Same posture as goods-in-from-manifest.)
- The legacy `POST /api/lots` direct-receive apportionment path (`CostAllocator`, `AllocationMethod`) is **untouched**; cost basis is the forward path for manual/manifest lots.
- Frontend: cost-basis config (strategy picker, anchor, per-strategy params, and a small MRP-band table editor) on the lot create + edit modals.
- All ratio math (percent, multiplier, rate) is integer-exact: parameters stored as scaled integers (basis points / milli-units), computed via `BigDecimal` then floored/rounded to `long` paise — no float (ArchUnit ban).
- The suggested selling price formula is **unchanged** (`ceil(unitCost ÷ (1 − margin%))`, margin category-first); only where the unit cost comes from changes.

## Capabilities

### New Capabilities
- `lot-cost-basis`: a lot declares a cost-basis strategy + anchor + parameters; the engine derives each product/batch per-unit cost from it, pins when the anchor is known, and records the amount-paid cross-check. Editing the basis is guarded by the lot freeze rule.

### Modified Capabilities
- `shelf-readiness`: when a batch becomes costed now depends on the lot's cost-basis strategy and whether its anchor (MRP/ASP) is yet known — not only on lot-close or manifest rate.
- `stock-ledger`: a `SALE`'s COGS draws on a unit cost that may now be derived from the lot's cost basis; an uncosted batch (anchor not yet known) still contributes zero COGS and never blocks a sale.

## Impact

- **Contracts**: new `CostBasisStrategy` + `CostAnchor` + `MultiplierBase` enums; append cost-basis fields to `CreateManualLotRequest` and `UpdateLotRequest`; a rate-band DTO; possibly a new `CostBasis` enum value per strategy.
- **Backend** (`inventory`): `Lot` gains cost-basis columns (+ a `lot_mrp_rate_band` child table); a `LotCostBasis` resolver that turns `(strategy, anchor, params, mrp/asp)` into a unit cost; wire it into `GoodsInCounting` (MRP-time pin) and the manual/import creation paths (immediate pin where possible); the amount-paid cross-check; `LotController` create/update accept + validate the basis on the existing endpoints, still `LotEditPolicy`-guarded.
- **Migration** `V46` (next free): lot cost-basis columns + `lot_mrp_rate_band` table + any `CostBasis` CHECK additions.
- **Frontend**: `LotManagement.tsx` create/edit modals gain the cost-basis section; `api.ts`/`types.ts` carry the new fields.
- **Out of scope**: the pricing/margin formula, and the legacy `POST /api/lots` apportionment receive path.
