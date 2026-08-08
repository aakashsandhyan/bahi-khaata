# Tasks — lot-cost-basis

## 1. Contracts

- [x] 1.1 New enums: `CostBasisStrategy` (FLAT_PER_UNIT, PERCENT_OF_ANCHOR, MRP_RATE_RANGE, MULTIPLIER), `CostAnchor` (MRP, ASP), `MultiplierBase` (ENTERED_UNIT_COST, ANCHOR, STATED_VALUE).
- [x] 1.2 `MrpRateBand` DTO record (minMrpPaise, maxMrpPaise nullable, costPaise) for the rate card. All money `long` paise (ArchUnit float ban).
- [x] 1.3 Append optional cost-basis fields to `CreateManualLotRequest` and `UpdateLotRequest`: strategy, anchor, flatUnitCostPaise, percentBp, multiplierMilli, multiplierBase, `List<MrpRateBand> rateBands`. Compact-ctor validation only sanity-checks present scalar ranges; full per-strategy validation lives in the backend.
- [x] 1.4 Add the cost-basis view fields to the lot summary DTO/`LotSummary` type so the edit modal pre-fills (strategy, anchor, params, bands).

## 2. Schema + entity

- [x] 2.1 Migration `V46__lot_cost_basis.sql`: add to `lot` — `cost_basis_strategy TEXT CHECK(...)`, `cost_anchor TEXT CHECK(MRP,ASP)`, `flat_unit_cost_paise INTEGER`, `percent_bp INTEGER`, `multiplier_milli INTEGER`, `multiplier_base TEXT CHECK(...)` (all nullable). Create `lot_mrp_rate_band(id, lot_id FK, min_mrp_paise, max_mrp_paise nullable, cost_paise, created_at)`. Existing lots leave all null (no declared basis).
- [x] 2.2 `Lot.java`: add the scalar cost-basis fields + getters/setters (field+setter pattern, constructors untouched — same as the category work). Represent percent/multiplier as `int`/`long` scaled — never float.
- [x] 2.3 `LotMrpRateBand` entity + `LotMrpRateBandRepository` (findByLotIdOrderByMinMrpPaise, deleteByLotId for re-set on edit).

## 3. Cost-basis resolver

- [x] 3.1 `LotCostBasis` component: `unitCost(lot, bands, anchorPaise, statedValuePaise) -> Money | null`. All ratio math in `BigDecimal`, HALF_UP to paise; percent via `/10_000`, multiplier via `/1_000`. Returns null when the required input (anchor / in-range band / base) is absent.
- [x] 3.2 `anchorValue(lot, batch, product)` helper: batch MRP when anchor=MRP, product online price when anchor=ASP.
- [x] 3.3 Per-strategy parameter validation (`requireValidBasis`): FLAT needs flatUnitCost; PERCENT needs percentBp+anchor; RANGE needs ≥1 band + anchor=MRP + non-overlapping ascending bands; MULTIPLIER needs multiplierMilli+base (+flatUnitCost when base=ENTERED). Throw IllegalArgumentException (→400) naming what's missing.
- [x] 3.4 Unit tests for the resolver: each strategy's formula + rounding; null on unknown anchor / out-of-range MRP / missing base; band boundaries (min inclusive, max exclusive, open top).

## 4. Wire into costing

- [x] 4.1 Immediate pin at creation/receipt for anchor-independent bases (FLAT_PER_UNIT, MULTIPLIER base=ENTERED_UNIT_COST): when a batch is created for a cost-basis lot, pin via `Batch.pinUnitCost(resolved)`.
- [x] 4.2 MRP-time pin: in `GoodsInCounting.countExpected`, after the MRP is recorded, if the lot declares a basis, derive+pin from it instead of the `rate × stated_value` path; a lot with no basis keeps the rate path.
- [x] 4.3 ASP-time pin: when `product.observeOnlinePrice` is set at import for an ASP-anchored cost-basis lot, derive+pin.
- [x] 4.4 Leave the legacy `POST /api/lots` apportionment path (`GoodsInService.receive` / `CostAllocator`) untouched.

## 5. Amount-paid cross-check

- [x] 5.1 A read-side check: `Σ(batch.allocatedTotal)` for a cost-basis lot vs `lot.amountPaid`; compute signed variance + a flag past a small tolerance. Surface on the lot summary (and/or the existing reconcile view). Never blocks.

## 6. Controller

- [x] 6.1 `LotController.createManualLot`: accept + `requireValidBasis` + persist the cost basis (scalars on the lot, bands as child rows); pin anchor-independent batches immediately (none exist yet at pure create — pins occur as products are added).
- [x] 6.2 `LotController.updateLot` (existing `PUT /{lotId}`, already `requireEditable`-guarded): apply cost-basis fields, validate, replace rate bands, and **re-derive+re-pin every not-yet-consumed batch** of the lot. Frozen lot → existing 409.
- [x] 6.3 Lot summary (`toSummary`) returns the cost-basis config + the variance/flag.

## 7. Frontend

- [x] 7.1 `types.ts` + `api.ts`: cost-basis fields on create/update bodies and `LotSummary`; the rate-band shape.
- [x] 7.2 `LotManagement.tsx` create + edit modals: a cost-basis section — strategy picker, anchor (shown only for anchor-dependent strategies), the relevant param inputs, and a small add/remove editor for the MRP rate bands. Pre-fill on edit; surface the 409 frozen + 400 validation messages in the existing banner.
- [x] 7.3 Show the amount-paid variance flag on the lot card when present.

## 8. Tests (backend)

- [x] 8.1 Create a lot per strategy → products costed correctly (flat immediate; percent/range pin at counting once MRP set; ASP pin at import; multiplier each base).
- [x] 8.2 Uncosted-until-anchor: MRP-anchored product uncosted before MRP, costed after; out-of-range band stays uncosted+flagged; sellable + zero COGS while uncosted.
- [x] 8.3 Edit basis on an unfrozen lot re-pins its batches; edit on a frozen (consumed) lot → 409; invalid params → 400.
- [x] 8.4 Amount-paid cross-check variance computed + flagged; does not block.
- [x] 8.5 A lot with no declared basis is unchanged (regression: existing rate/apportionment costing still passes).

## 9. Verify + ship

- [x] 9.1 `./gradlew test` green (incl. ArchUnit float ban + `ddl-auto=validate` for V46) and `cd dashboard/web && npm run build` clean.
- [ ] 9.2 `/opsx:verify`, then a migrating shop deploy (V46) per the deploy runbook (backup DB first), then `/opsx:archive`.
