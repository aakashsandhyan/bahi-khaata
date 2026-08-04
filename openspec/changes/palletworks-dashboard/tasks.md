## 1. Contracts + backend aggregate

- [ ] 1.1 `DashboardView` record (nested: kpis, funnel stages, alerts with target view key, recent sales reusing `SaleSummary`) in the `contracts` module
- [ ] 1.2 `dashboard` package: read-only `DashboardRepository` with native single-pass aggregate queries (revenue window, received/priced/backlog, recovery sums, funnel, five alert counts) per D3/D6
- [ ] 1.3 IST "today" window helper per D4 (Java-computed UTC string range, half-open); staleness constant N=3 with comment
- [ ] 1.4 `DashboardService` assembling the DTO (zero-bill average guard, no-lots recovery guard, zero-count alerts omitted, `Checkout.recentSales(5)`); `DashboardController` at `GET /api/dashboard`
- [ ] 1.5 Backend tests: IST window boundaries (early-morning IST sale counted; UTC-naive date would fail), empty-DB payload (no divide-by-zero, alerts empty), populated aggregate correctness
- [ ] 1.6 `./gradlew :backend:test` green; commit backend

## 2. e2e seed

- [ ] 2.1 Seed two `sale` + `sale_line` rows stamped `strftime('%Y-%m-%dT%H:%M:%S.000Z','now')`, fixed amounts/bill numbers, per D10; constants mirrored in `e2e/seed.ts`
- [ ] 2.2 Fresh `npm run e2e` boot applies seed cleanly; existing 16 specs still green; commit seed

## 3. Frontend

- [ ] 3.1 `types.ts` DTO mirror + `api.ts` `dashboard.get()`
- [ ] 3.2 `Dashboard.tsx`: KPI strip (GST tile carries "not yet computed" label), funnel bars, alert list rows calling `onNavigate(target)`, recent-sales rail linking to Sales, plain error state that never blocks navigation
- [ ] 3.3 Dash styles in `styles.css` on existing tokens/classes only (no new one-off system)
- [ ] 3.4 `Sidebar.tsx`: `'dashboard'` view first in Operations, kicker "Overview"; `App.tsx`: desktop landing → dashboard, render chain entry; phone landing untouched
- [ ] 3.5 Build + tsc green; commit frontend

## 4. Smoke tests

- [ ] 4.1 `13-dashboard.spec.ts`: loads as desktop landing (no click needed), four tiles render, revenue tile asserts exact seeded total (non-zero — timestamp-format tripwire), GST tile shows honesty label, alert row click navigates to its screen, seeded sale visible in recent rail
- [ ] 4.2 `14-sales.spec.ts`: Sales screen lists both seeded sales with totals
- [ ] 4.3 `15-receipt-config.spec.ts`: loads; seeded TEST-NET config shown disabled; no connection attempted beyond any explicit unroutable test path
- [ ] 4.4 `16-bill-settings.spec.ts`: loads; V43 defaults displayed; edit+save round-trips
- [ ] 4.5 Suite green twice consecutively (determinism); commit tests

## 5. Verify & finish

- [ ] 5.1 Full `npm run e2e` + `./gradlew :backend:test` green
- [ ] 5.2 Visual walk-through with seeded backend: Dashboard at desktop + 420px; spot-check tiles against seed math by hand
- [ ] 5.3 Payload timing observed in smoke run (fast on dev implies fine on shop; note figure); update change checkboxes; ready for review/PR
