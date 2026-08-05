## Why

Receiving a lot is one job spread across three screens: Receiving takes boxes at the door, Lots lists deliveries, and reconciliation hides behind both — nobody can see a lot's whole state (what arrived, what's counted, what's short, what it cost) in one place. The approved Palletworks design treats intake as a single screen with lot tabs, header stats, a step strip, and a lot-math rail. Phase 5 builds that hub from the flows that already exist (scope approved in session 2026-08-06: one Intake screen replacing Receiving and Lots; Unpacking stays the phone counting station; final step is reconcile-and-close; no seller-claim tracking).

## What Changes

- New **Intake** screen (Operations, replacing the Receiving and Lots entries — nav goes twelve to eleven): a lot rail of open deliveries; per-lot header stats (manifest MRP, amount paid, cost-of-MRP %, counted x of y); an inferred four-step strip (Manifest in / Manual → Counting → Reconcile → Close); three per-lot tabs — Boxes (today's receive / not-received / damaged flow), Lines (manifest table: expected, counted, delta, condition split, MRP), Reconcile & close (existing reconciliation view, the close gate, and a counted-awaiting-pricing hand-off link); a lot-math right rail (effective cost per unit, short/over, MRP found, projected retail over priced units, close or receiving-finished action).
- Manual lots get the counting-is-the-manifest framing: discovered lines instead of a manifest table, the add-product entry, provisional cost-per-unit that falls as counting continues.
- Lot creation moves into Intake from the retired Lots screen — manual lots only: manifest import has never been a dashboard flow (lots arrive pre-imported via the backend importer), and Intake does not change that.
- Step state is inferred from existing data; no new state columns, no schema change.
- Backend: at most one thin per-lot stats aggregate if existing summaries cannot answer the header strip — decided in design after reading what `LotSummary`, the reconciliation endpoint, and `catalog.browse` already return.
- e2e: Receiving and Lots smokes rewritten as Intake specs; shell/probe assertions go to eleven entries.

## Capabilities

### New Capabilities
- `pallet-intake`: the Intake hub — lot rail, header stats, step inference, box handling, manifest/discovered lines, reconcile-and-close, lot math rail, manual-lot framing, pricing hand-off.

### Modified Capabilities
- `dashboard-shell`: Operations loses Receiving and Lots, gains Intake (eleven entries).
- `dashboard-smoke-tests`: coverage list follows the nav; Receiving/Lots specs become Intake specs.
- `goods-in-reconciliation`: reconciliation and the close gate are surfaced inside Intake (requirement wording moves from screen-agnostic to the Intake context where it names screens; behavior unchanged).

### Removed Capabilities

None — Receiving and Lots behavior relocates wholesale into Intake; the specs record the move, not a removal of behavior.

## Impact

- Frontend: new `Intake.tsx` (+ subcomponents), `Receiving.tsx` and `LotManagement.tsx` retired after parity, `Sidebar.tsx`/`App.tsx` nav updates, the Reconcile & close tab wires the existing but dashboard-dormant close-gate endpoints (`unopened`, `close`); `LotReconcile.tsx` stays where it belongs, in Pricing — it is a pricing-time phantom write-off tool that reads zero before pricing.
- Backend: read-only; possibly one stats endpoint in the existing `inventory`/lot controller area. No writes beyond flows that already exist (receive/reject box, receiving-finished, close, create lot, add product).
- e2e: 02 and 10 rewritten, probe updated; suite size ≈ steady.
- Risk: the two retired screens carry daily-use flows (door receiving, lot creation) — parity proven by rewritten smokes before deletion, same delete-last discipline as the Catalog fold.
