## Context

Receiving a lot is spread across two screens that each hold half the job: `Receiving.tsx` scans boxes at the door and does manual-lot entry; `LotManagement.tsx` lists deliveries and creates lots. Neither shows a lot's whole state — what arrived, what's counted, what's short, what it cost. The moving parts already exist and are honest. `App.tsx` holds `view` with a param-less `setView` (no router — phase-1 decision). `receiving.lots()` (`GET /api/lots`) returns `LotSummary` for **open lots only** (`Lot::isOpen` filter), carrying box tallies but no money. `unpacking.deliveries()` returns `DeliveryProgress` with unit counts (`unitsCounted`/`unitsExpected`) per lot. `catalog.browse(q,status,cat,page,size,lot)` returns `CatalogEntry{unitsExpected,unitsCounted,status,priced,categoryCode}` per product, lot-scoped. The manifest carries **cost, not MRP** — `ExpectedLine` has `statedValue` + expected/counted, MRP is read per unit at counting (`recordedMrpPaise`). `LotClosing.crossCheckCost` computes paid-vs-pinned but is **not exposed by any endpoint**; `POST /api/unpacking/lots/{lotId}/close` and `GET …/unopened` exist but **no dashboard client calls them today**. `LotReconcile.tsx` is phantom-write-off, mounted in `PricingWorkbench` — an end-of-pricing concept. Manifest **import is not a dashboard flow** (`/api/consignments/import` has no client; `LotManagement`'s manifest option says "not yet supported here").

## Goals / Non-Goals

**Goals:**
- One Intake screen (Operations) replacing Receiving and Lots; nav twelve to eleven; every relocated flow re-homed with parity proven before deletion.
- A lot's whole state on one screen: rail, header stats, inferred step strip, three tabs, lot-math rail — step state inferred frontend-side, no schema change, no new state column.
- At most one thin read-only stats endpoint, and only for figures the existing summaries genuinely cannot answer.

**Non-Goals:**
- No router, no manifest-import UI (out of dashboard today; manifest lots arrive pre-imported), no seller-claim/grade tracking, no list-price-at-intake, no money-path or receiving-behavior change.
- No move of `LotReconcile` (phantom write-off) into Intake — it is a pricing-time step and stays in Pricing.

## Decisions

**D1 — `Intake.tsx` is a state-switched hub (no router), decomposed to keep every part under ~200 lines.** An orchestrator (state + fetch, ~250 lines) renders `LotRail`, `LotHeaderStats`, `StepStrip`, the active tab (`BoxesTab` / `LinesTab` / `ReconcileCloseTab`), `LotMathRail`, and `CreateLotModal`. *Rejected:* one 1000-line screen (phase-1 lesson: large screens hurt); a router (phase-1 chose `setView`).

**D2 — The lot rail lists open lots only, from `receiving.lots()`, in-progress before receiving-complete.** `GET /api/lots` already filters to `Lot::isOpen` and sorts that way, so a closed lot drops off the rail on its own — the same disappearance Receiving/Lots show today. *Rejected:* open + recent-closed (no endpoint returns closed lots; a new query for a rarely-needed backward glance is out of scope).

**D3 — Lot creation is a rail-header "+ New lot" button opening `CreateLotModal`, extracted from `LotManagement`, offering manual lots only.** Manifest import has no dashboard path (verified), so the modal drops the dead "Manifest-based" option; manifest lots continue to arrive pre-imported via `/api/consignments/import`. *Rejected:* keeping the manifest radio (it only ever shows "not yet supported here"); building manifest import now (out of approved scope).

**D4 — The four-step strip is inferred frontend-side from `LotSummary` + `DeliveryProgress`, no new column.** Step 1 (Manifest in / Manual) is always past once the lot exists; **Counting** is active while boxes are non-terminal or `unitsCounted < unitsExpected` — for a **manual lot**, which has no boxes to go terminal, `receivingComplete` is the gate (set by hand via `markReceivingComplete`); **Reconcile** is reached when counting is done but the lot is still open; **Close** is the terminal *action*, not a listed state — a closed lot has already left the rail (D2). *Rejected:* a persisted step column (the data already implies the step; a column would drift from it).

**D5 — Header stats and the right-rail math come from ONE new thin read-only endpoint, `GET /api/lots/{lotId}/stats`, in the `inventory` package mirroring `LotController`.** The existing summaries cannot answer paid, MRP, cost-of-MRP, effective cost/unit, or projected retail; `LotSummary` has no money and `CatalogEntry` no price. One aggregate composes `lot.amountPaid`, the pinned/paid figures from `LotClosing.crossCheckCost`, the sum of batch `recordedMrp` over counted units, `pricedUnits` × price for projected retail, and expected/counted — feeding both strips from a single call. *Rejected:* widening `LotSummary` (drags money onto a hot receiving list every screen reads); computing on the client (paid, pinned cost, and per-batch MRP are not in any payload the browser already holds).

**D6 — The "manifest MRP" header stat is honestly *cumulative MRP found*; header and rail share the one figure.** The manifest carries cost, not MRP (D-context) — there is no manifest-stated MRP to total. MRP is discovered unit by unit at counting, so the only honest MRP figure rises as counting continues. Cost-of-MRP % is paid ÷ MRP-found, labelled provisional. *Rejected:* showing a "manifest MRP total" (it does not exist — it would be fabricated).

**D7 — The Boxes tab reuses Receiving's flow, extracted as `BoxesTab`; `receiving.*` endpoints unchanged.** Manifest lots keep the scan / receive / not-received / damaged box list (`receiving.boxes` + the three writes); manual lots keep counting-is-the-manifest framing — discovered lines, the add-product form, provisional cost/unit (paid ÷ counted, falling as counting continues). *Rejected:* a rewrite (the door-receiving flow is daily-use and correct — relocate, don't reimplement).

**D8 — The Lines tab (manifest lots) is `catalog.browse(…, lot)`: Product, Mfst (expected), Counted, Δ.** All four are computable from `CatalogEntry` (`Δ = unitsCounted − unitsExpected`). The mockup's **Grade** column is dropped — condition is a per-batch fact, absent from `CatalogEntry`, and our vocabulary is conditions-not-grades with no grade at intake; the **MRP** and **List price** columns are dropped — neither is on `CatalogEntry`, and there is no list-price-at-intake. *Rejected:* a per-line MRP/condition join (would fatten the thin stats endpoint into a per-row payload for columns the honest intake view doesn't claim).

**D9 — Reconcile & close = goods-in cross-check + the close gate + a pricing hand-off; `LotReconcile` is NOT reused.** The tab shows shorts/over and paid-vs-pinned (from D5's stats endpoint, satisfying goods-in-reconciliation), the unopened-cartons gate (`GET …/unopened`, then confirm), a **Close** action wiring the existing `POST /api/unpacking/lots/{lotId}/close` (a new client fn — the endpoint has no caller today), and a link to Pricing for counted-awaiting-pricing goods. `LotReconcile` (phantom write-off) stays in `PricingWorkbench`: it nets double-counts that only exist after pricing, so at intake it would always report zero. The rail's action is Close (fully counted) or Receiving-finished (`markReceivingComplete`, still receiving). *Rejected:* embedding `LotReconcile` here (the proposal's impact bullet named it, but it is a pricing-time tool that would show an empty report pre-pricing — recorded as a finding).

**D10 — Delete last: re-home, rewrite smokes, go green, then retire the two screens.** (1) `Intake.tsx` + subcomponents + `intake.stats` client + `unpacking.closeLot`/`unopened` clients; (2) nav 12→11 in `Sidebar.tsx`/`App.tsx` (remove Receiving + Lots, add Intake); (3) rewrite e2e 02/10 as Intake specs and update `probe.spec.ts` to eleven entries, scenario→named-test; (4) only once green, delete `Receiving.tsx` and `LotManagement.tsx`. *Rejected:* deleting first (strands door-receiving and lot-creation mid-fold — the proposal's named risk, same discipline as the Catalog fold's D9).

## Risks / Trade-offs

- [The new stats endpoint is Intake's sole source for money figures — a change to it silently breaks the header and rail] → its shape is covered by the rewritten Intake smoke; the coupling is explicit here.
- [Wiring the long-dormant `close` endpoint from the UI could surface behavior nobody has exercised] → `LotClosingTest` already covers the gate and the unopened-cartons path; the UI only calls it.
- [Inferred steps could disagree with an operator's mental model on an odd lot] → steps are advisory framing over the same tallies the tabs show; the tabs, not the strip, drive every action.

## Migration Plan

1. Frontend additive: `Intake.tsx` + `LotRail`/`LotHeaderStats`/`StepStrip`/`BoxesTab`/`LinesTab`/`ReconcileCloseTab`/`LotMathRail`/`CreateLotModal`; `api.ts` gains `intake.stats(lotId)`, `unpacking.closeLot`/`unopened`; `Sidebar.tsx`/`App.tsx` nav 12→11 (Receiving + Lots out, Intake in).
2. Backend: one method `GET /api/lots/{lotId}/stats` on the `inventory` lot controller composing existing services (`crossCheckCost`, batch MRP sums, expected/counted) into a `LotIntakeStats` record; a unit test. No other backend change; no schema, printer, or money path touched.
3. Rewrite e2e 02 (Intake: rail, tabs, box receive on the seeded lot) and 10 (lot creation + reconcile/close), update `probe.spec.ts` to eleven entries; `npm run e2e` green is the gate.
4. Delete `Receiving.tsx` and `LotManagement.tsx` last, after green (D10).
5. At sync: add `pallet-intake`; reword `dashboard-shell` (eleven entries, Intake replaces Receiving/Lots) and `dashboard-smoke-tests` (02/10 become Intake); move `goods-in-reconciliation`'s screen-naming to the Intake context, behavior unchanged. Deploy = existing jar-swap; rollback = previous jar.

## Open Questions

- Whether `intake.stats` should also return per-line shorts (folding in `remediation.shortsInLot`) or the Reconcile tab should call that separately — ships calling it separately (keeps the stats endpoint an aggregate, reuses an existing call). Non-blocking.
- Whether a fully-counted manual lot should auto-suggest Close or wait for "Receiving finished" first — ships offering Receiving-finished, since a manual lot's close gate differs. Non-blocking.
