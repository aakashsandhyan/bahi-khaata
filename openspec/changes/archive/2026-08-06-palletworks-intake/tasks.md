## 1. Stats endpoint

- [x] 1.1 `GET /api/lots/{lotId}/stats` → `LotIntakeStats` in contracts (paid, mrpFoundPaise, costOfMrpPercent nullable, countedUnits, expectedUnits nullable, shortUnits, overUnits, effectiveCostPerUnitPaise nullable, projectedRetailPaise over priced units); single-pass queries, guards for zero counted (D5/D6)
- [x] 1.2 Backend tests: populated lot, empty lot (all nullables null, no divide-by-zero), manual lot
- [x] 1.3 `./gradlew :backend:test` green

## 2. Intake screen

- [x] 2.1 `api.ts`: `intake.stats(lotId)`, `unpacking.unopened(lotId)`, `unpacking.closeLot(lotId, confirm)` clients; `types.ts` mirror
- [x] 2.2 `Intake.tsx` orchestrator (~250 lines max) + subcomponents each ≤200: `IntakeRail` (open lots, badges, + New lot), `IntakeHeader` (stats strip, honest dashes), `IntakeSteps` (D4 inference), `BoxesTab` (extracted Receiving flow, behavior identical), `LinesTab` (catalog.browse(lot): Product/Expected/Counted/Δ), `ReconcileCloseTab` (shorts/overs, unopened list, close with confirm-over-unopened, receiving-finished for manual, pricing hand-off link), `LotMathRail`, `CreateLotModal` (extracted from LotManagement, manual only)
- [x] 2.3 Manual-lot framing: discovered lines, add-product, counting-is-manifest note, provisional cost/unit
- [x] 2.4 `Sidebar.tsx`/`App.tsx`: Intake second (Receiving's slot), Receiving+Lots entries removed (eleven); tsc + build green

## 3. Smokes

- [x] 3.1 `02-intake.spec.ts` (replaces 02-receiving): rail lists seeded lot; stats strip renders with honest values; step strip shows Counting; Boxes tab receives E2E-BOX-002 and badge updates
- [x] 3.2 `10-intake-lots.spec.ts` (replaces 10-lots): create manual lot; receiving-finished moves badge; Lines tab shows expected/counted Δ for seeded widget; Reconcile tab lists unopened carton and close-with-confirm closes the created lot (assert it leaves the rail)
- [x] 3.3 probe to eleven entries; any spec navigating via Receiving/Lots updated
- [x] 3.4 Delete `Receiving.tsx` + `LotManagement.tsx` LAST; full suite green once (fast gate)

## 4. Verify & finish (main session)

- [x] 4.1 Suites green twice + backend green (independent)
- [x] 4.2 Visual walk desktop + 420px; prod-copy check (8 real lots through rail/stats/steps; timing)
- [x] 4.3 Scenario→named-test map or unproven list; checkboxes; PR-ready
