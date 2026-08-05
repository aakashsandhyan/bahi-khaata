## Why

The dashboard has no home: the operator lands on the Till and every question — how did we sell today, what is stuck unpriced, what needs a decision — requires visiting several screens and adding numbers by hand. Phase 1 (palletworks-foundation, merged) delivered the visual language, shell, and smoke-test harness; the bill-printing merge delivered real sales data (`sale`/`sale_line`). Phase 2 builds the Palletworks Dashboard screen on top of both: one glance answers revenue today, pipeline state, recovery on spend, and what needs attention. Design approved in session 2026-08-05.

## What Changes

- New backend aggregate endpoint `GET /api/dashboard` returning one DTO: four KPI tiles (revenue today with bill count and average, received-vs-priced units with unpriced backlog, all-time recovery on lot spend, GST collected reading `sale.tax_paise` — ₹0 until the separate gst-inclusive-pricing change ships), the received → priced → sold funnel, "needs a decision" alerts computed from real signals (unpriced counted units, needs-work backlog, pending captures, labels held in review, open lots with receiving incomplete), and the five most recent sales.
- New `Dashboard.tsx` screen rendering that payload in the Palletworks dash layout: KPI strip, funnel bars, alert list (each row navigates to its owning screen), recent-sales rail.
- Desktop landing screen changes from Till to Dashboard; sidebar gains a Dashboard entry. Phone landing behavior (Unpacking / Capture) is unchanged.
- e2e seed extended with deterministic sales fixtures; new Dashboard smoke test; the existing 16 tests keep passing.
- No till changes, no GST computation, no charts (category recovery and sell-through curves belong to the Analytics phase).

## Capabilities

### New Capabilities
- `dashboard-home`: the Dashboard screen and its aggregate API — KPI definitions, funnel, alert signals and navigation, recent sales, landing-screen behavior.

### Modified Capabilities
- `dashboard-shell`: the sidebar gains a Dashboard entry and the desktop default view moves from Till to Dashboard (requirement-level change to the shell's navigation and landing behavior).
- `dashboard-smoke-tests`: coverage requirement extends to the new screen (every screen must have a smoke test; Dashboard is now a screen).

## Impact

- Backend: new read-only controller + service + DTO under a `dashboard` package; aggregation via existing repositories/SQL over `sale`, `sale_line`, `stock_ledger`, `batch`, `product`, `lot`, `product_capture`, `print_job`. No schema change, no writes.
- Frontend: `Dashboard.tsx` (new), `Sidebar.tsx` (nav entry + View type), `App.tsx` (landing + render chain), `api.ts`/`types.ts` (client + DTO mirror), `styles.css` (dash-specific classes on existing tokens).
- e2e: seed gains `sale`/`sale_line` rows (structure-deterministic; today-relative timestamps only where the revenue-today tile needs them), new `13-dashboard.spec.ts`, shell spec updated for the new landing.
- Risk: aggregate queries over the append-only ledger must stay cheap on the shop machine — single-pass SQL, no N+1; measured in the smoke run.
