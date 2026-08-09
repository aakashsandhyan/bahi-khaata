# Lot cost basis — design

## Context

Cost is derived one way today. `POST /api/lots` apportions the amount paid across lines by weight (`CostAllocator`, `AllocationMethod.RELATIVE_MRP`); the manifest/counting path pins each line at `rate × stated_value`, `rate = (amountPaid+freight) ÷ Σ(stated_value×qty)` (`GoodsInCounting`, BigDecimal). Cost lives on the **batch** (`allocatedUnitCost`, `allocatedTotal`, `costBasis`, `isCosted()`); `Batch.pinUnitCost(unitCost)` sets `PINNED` and keeps the total in step with quantity. MRP is per-batch (recorded at counting); ASP is `product.onlinePrice` (observed at import); the manifest per-line value is `stated_value`. Money is integer paise; ratio math uses `BigDecimal` then `long` (ArchUnit bans float). The lot is now editable via `PUT /api/lots/{lotId}` guarded by `LotEditPolicy.requireEditable` (frozen once stock is consumed).

None of the shop's real buying terms — flat per-piece, a % of MRP/ASP, an MRP rate card, a multiplier — can be expressed. This design puts a **cost basis on the lot** and derives batch cost from it.

## Goals / Non-Goals

**Goals:**
- A lot declares `{ strategy, anchor, params }`; the engine derives each batch's unit cost from it.
- Four strategies: FLAT_PER_UNIT, PERCENT_OF_ANCHOR, MRP_RATE_RANGE, MULTIPLIER (base = entered cost / anchor / stated value).
- Anchor explicit per lot (MRP or ASP). Cost pins when the anchor is known; uncosted until then (zero COGS, still sellable).
- Amount paid kept as a cross-check, not the cost driver.
- Set at lot creation and edited on the existing `PUT /api/lots/{lotId}`, freeze-guarded.
- Integer-exact; suggested-price formula unchanged.

**Non-Goals:**
- The pricing/margin formula (`Margins`, `TargetMargins`) — unchanged; only the unit-cost source changes.
- The legacy `POST /api/lots` apportionment receive path (`CostAllocator`, `AllocationMethod`) — left as-is for that flow.
- Per-product cost overrides — cost basis is lot-level; a product's cost is derived, then can still be corrected by the existing pinning path if needed.

## Decisions

### 1. Cost basis is lot-level config; the batch just holds the derived cost
The strategy/anchor/params live on the **lot**. The batch keeps holding a plain pinned unit cost (reuse `CostBasis.PINNED` — **no new `CostBasis` enum value**, avoiding CHECK churn and per-batch strategy sprawl). "How it was derived" is answered by the lot's cost basis; "what it costs" stays on the batch as today. `Batch.pinUnitCost` is the single write path for a derived cost.

### 2. Data model
`lot` gains (migration V46):
- `cost_basis_strategy TEXT` CHECK in (`FLAT_PER_UNIT`,`PERCENT_OF_ANCHOR`,`MRP_RATE_RANGE`,`MULTIPLIER`) — nullable (a lot may still use the legacy path / no declared basis).
- `cost_anchor TEXT` CHECK in (`MRP`,`ASP`) — required by the anchor-dependent strategies, null otherwise.
- `flat_unit_cost_paise INTEGER` — FLAT_PER_UNIT, and MULTIPLIER base = ENTERED_UNIT_COST.
- `percent_bp INTEGER` — PERCENT_OF_ANCHOR, percent in **basis points** (30% = 3000).
- `multiplier_milli INTEGER` — MULTIPLIER, multiplier in **milli-units** (1.25× = 1250).
- `multiplier_base TEXT` CHECK in (`ENTERED_UNIT_COST`,`ANCHOR`,`STATED_VALUE`).

Scaled integers, never float: percent as basis points, multiplier as milli-units. This keeps every parameter a `long`/`int` column and every field ArchUnit-clean; the division happens transiently in `BigDecimal`.

`lot_mrp_rate_band` child table (MRP_RATE_RANGE):
```
lot_mrp_rate_band(
  id CHAR(36) PK, lot_id CHAR(36) FK->lot,
  min_mrp_paise INTEGER NOT NULL,      -- inclusive
  max_mrp_paise INTEGER,               -- exclusive; NULL = open top band
  cost_paise INTEGER NOT NULL,
  created_at TEXT NOT NULL )
```
A child table (not JSON) so bands are queryable, checkable, and edited as rows — consistent with the rest of the schema. Bands are validated non-overlapping and ascending on save.

### 3. The resolver — `LotCostBasis`
A pure component `LotCostBasis.unitCost(lot, anchorPaise, statedValuePaise) : Money | null` (null = "anchor not yet known / product out of all bands" → leave the batch uncosted). All math `BigDecimal`, `HALF_UP` to the paise (matching the existing `pinnedUnitCostFor`):
- **FLAT_PER_UNIT** → `flat_unit_cost_paise` (anchor-independent; always known).
- **PERCENT_OF_ANCHOR** → `round(anchorPaise × percent_bp ÷ 10_000)`; null if anchor unknown.
- **MRP_RATE_RANGE** → the `cost_paise` of the band containing the MRP (`min ≤ mrp < max`, open top); null if the MRP is out of every band (flagged, not guessed).
- **MULTIPLIER** → `round(base × multiplier_milli ÷ 1_000)` where base = `flat_unit_cost_paise` (ENTERED_UNIT_COST), `anchorPaise` (ANCHOR), or `statedValuePaise` (STATED_VALUE); null if the needed base is unknown.

`anchorPaise` is `batch.getMrp()` when anchor=MRP, `product.getOnlinePrice()` when anchor=ASP.

### 4. Resolution timing — where the pin happens
Cost pins the moment its inputs exist; otherwise the batch stays uncosted (`isCosted()=false`), exactly today's behaviour (zero COGS, sellable, priceable later).
- **FLAT_PER_UNIT** and **MULTIPLIER base=ENTERED_UNIT_COST**: known at lot creation → pin batches as they are created/received.
- **anchor=MRP** (PERCENT/RANGE/MULTIPLIER-ANCHOR): MRP is recorded at counting → pin inside `GoodsInCounting.countExpected` right after the MRP is set, replacing the current `rate × stated_value` pin **for cost-basis lots** (the legacy rate pin stays for lots with no declared basis).
- **anchor=ASP**: `product.onlinePrice` is known at import → pin at import for cost-basis lots.
- **STATED_VALUE base**: known at import/counting → pin then.

The single write is always `batch.pinUnitCost(resolved)`; a lot without a declared basis keeps the current path untouched.

### 5. Amount paid → cross-check
For a cost-basis lot the amount paid no longer drives cost. A read-side check compares `Σ(batch.allocatedTotal)` for the lot against `lot.amountPaid` and reports a signed variance + a flag when it exceeds a small tolerance (surfaced on the lot summary / a reconcile view). Recorded, never enforced — useful for spotting a mistyped amount or a bad rate card, mirroring goods-in-from-manifest.

### 6. Editing the basis — reuse the freeze guard
Cost-basis fields append to `UpdateLotRequest` and flow through the existing `PUT /api/lots/{lotId}` → `LotEditPolicy.requireEditable` (409 once any stock consumed — changing the basis would rewrite sold-stock COGS). On an allowed edit (lot unfrozen ⇒ nothing consumed) the handler **re-derives and re-pins every batch of the lot** so the change takes effect on already-counted-but-unsold stock. Validation is per strategy: FLAT needs `flat_unit_cost`; PERCENT needs `percent_bp` + anchor; RANGE needs ≥1 valid band + anchor=MRP; MULTIPLIER needs `multiplier_milli` + base (+ `flat_unit_cost` when base=ENTERED). Bad/ missing params → 400.

### 7. Creation
`CreateManualLotRequest` gains the same optional cost-basis fields; a manual lot can declare its basis up front. Absent basis = today's behaviour. Import (`ImportLot`) can carry a basis later; not required in this change (manual + the shared resolver first).

## Risks / Trade-offs
- **Cost unknown until counting for MRP-anchored lots** — accepted; identical to today's pin-at-count, and the batch is sellable meanwhile.
- **Rate-card gaps**: an MRP outside every band leaves the item uncosted + flagged rather than guessing a cost. Operators must cover their ranges (open top band handles the tail).
- **Re-pin on edit** touches all of a lot's batches; safe because edit is only allowed while unfrozen (no consumption), so no COGS is rewritten. Cost is bounded by lot size.
- **ASP quality**: ASP is the observed marketplace price averaged per product; a % of ASP is only as good as that observation. Anchor is explicit so the operator chooses MRP when ASP is unreliable.
- **Scaled-int precision**: basis points (0.01%) and milli-units (0.001×) are finer than the shop needs; rounding is HALF_UP to the paise, consistent with the existing rate pin.
- **Two cost paths coexist** (legacy apportionment vs cost basis). Kept deliberate and narrow: cost basis is opt-in per lot via a declared strategy; a lot with none behaves exactly as before.
