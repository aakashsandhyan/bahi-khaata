## Why

The dashboard works but looks unfinished and shows the operator very little: twelve visually flat screens, no shared identity, no test coverage of any of them. A complete "Palletworks" redesign has been approved (design session artifact `reference/Palletworks.dc.html` + `reference/modernist-styles.css`) and will be delivered in eight phases. This change is phase 1: adopt the Palletworks visual language across every existing screen, replace the top navigation with the grouped sidebar shell, and stand up the Playwright smoke-test harness that all later phases build on. Later phases (Dashboard KPIs, Invoices, Inventory/item detail, Analytics, Returns, Customers, Settings) each get their own change.

## What Changes

- Replace the dashboard's visual foundation with the Palletworks/modernist design system: new `:root` tokens (bg `#f3f2f2`, surface `#eae9e9`, ink `#201e1d`, accent `#ec3013`, neutral 100–900 ramp), zero border-radius, 2px dividers, Archivo 400/600/800 self-hosted as woff2 (no network font fetch — the till must boot offline).
- Port the design-system component classes (`btn`/`btn-primary`/`btn-secondary`/`btn-ghost`, `tag-*`, `seg`, `input`, `field`, `table`, `card`, `hr`, dialog) and restyle existing screen-specific classes on top of them.
- Replace the top navigation bar with a 236px sticky sidebar: brand block, grouped nav (Operations / Selling / Back office), signed-in operator footer; per-screen header with uppercase kicker + title. Sidebar collapses to a drawer at ≤760px; phone-only Capture stays hidden from desktop nav as today.
- Sweep all 12 screens (Checkout, Receiving, Unpacking, Prep, Pricing, PricingWorkbench, Lots, Capture, Review, Reprint, Catalog, Suppliers, Printer config) into the new language. Presentation only: no logic changes, no new endpoints, no DOM rewrites beyond what styling demands. The Checkout teal/coral accent is retired — one red-accent language everywhere.
- Add a Playwright end-to-end smoke-test harness under `dashboard/web/e2e/` that boots the real Spring Boot backend against a deterministic seeded SQLite database plus the Vite dev server, with at least one smoke test per screen (screen loads, key elements render, one core interaction works).

## Capabilities

### New Capabilities
- `dashboard-design-language`: the shared visual system every dashboard screen must follow — tokens, typography, component classes, money formatting (tabular numerals), single accent identity, offline-safe font loading.
- `dashboard-shell`: the application frame — grouped sidebar navigation, per-screen header, operator identity footer, responsive collapse behavior, phone-screen visibility rules.
- `dashboard-smoke-tests`: end-to-end smoke coverage — every dashboard screen has a Playwright test running against a real backend with a deterministic seed fixture; the suite is a pre-deploy gate.

### Modified Capabilities

None. Existing capability requirements (shelf-pricing, goods-in-reconciliation, label-print, etc.) are untouched — this change alters presentation and adds test coverage, not behavior.

## Impact

- `dashboard/web/src/styles.css` — rewritten on the new tokens (largest single diff).
- `dashboard/web/src/App.tsx` — topnav replaced by sidebar shell; view-switching logic unchanged.
- All 12 screen components under `dashboard/web/src/` — class/markup adjustments to adopt shell and components; no behavioral edits.
- `dashboard/web/` — new dev-dependency `@playwright/test`; new `e2e/` directory, seed SQL fixture, and npm scripts; Vite config untouched except any test-server wiring.
- Backend — no production code changes; a seed profile/fixture for e2e may be added under test resources only.
- Risk: visual regression across every screen at once; mitigated by the per-screen smoke suite landing in the same change and by screen-by-screen commits.
