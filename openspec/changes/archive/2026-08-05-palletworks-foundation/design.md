## Context

The web dashboard (`dashboard/web`, React 18 + Vite + plain CSS) has twelve working screens styled by one `styles.css` (~560 lines) built on ad-hoc tokens (`--ink`, `--brand` indigo, `--s1..s6`, rounded corners, per-screen accent colors — teal/coral on the till). Navigation is a horizontal topnav hidden on phones; view switching is React state in `App.tsx`, no router. There are no frontend tests of any kind.

The approved Palletworks design (see `reference/Palletworks.dc.html` and `reference/modernist-styles.css`) defines a different language: Archivo, warm-gray ground, near-black ink, one red accent, zero radius, 2px dividers, a 236px grouped sidebar, uppercase kickers, dense tables, tabular numerals for money. This change re-grounds the existing app in that language and adds the smoke-test harness later phases extend. Behavior, endpoints, and data contracts do not change.

## Goals / Non-Goals

**Goals:**
- One visual system, applied to every existing screen, sourced from a single rewritten `styles.css`.
- Sidebar shell with grouped navigation and per-screen header, responsive down to the phone screens that warehouse staff use today.
- Playwright smoke suite against the real backend + seeded SQLite, one test minimum per screen, runnable with a single npm script; pre-deploy gate.
- Font loading that works with no internet at the shop.

**Non-Goals:**
- No new screens (Dashboard, Analytics, Invoices, etc. are later phases).
- No new backend endpoints or schema; no behavior change visible in API traffic.
- No React Router migration; no component-library dependency; no dark mode.
- No visual-regression screenshot testing (may arrive in a later phase; smoke = functional presence, not pixel diffing).

## Decisions

**D1 — Rewrite `styles.css` wholesale on the modernist tokens; retire the old token names.**
Old tokens (`--ink`, `--brand`, `--s1..s6`, `--r1..r3`) are replaced by the DS names (`--color-text`, `--color-accent`, `--space-*`, `--radius-* = 0`). Screen CSS classes keep their names (`.till*`, `.unpack*`, `.prep*` …) but their rules are re-expressed in the new tokens.
*Rejected:* an alias layer mapping old names to new (two names for every value, forever); a parallel new stylesheet with gradual migration (two design systems live at once, exactly the dull-mixed state we're leaving).

**D2 — Self-host Archivo 400/600/800 as woff2 with `@font-face`; drop the Google Fonts `@import` that the reference CSS uses.**
Files live in `dashboard/web/src/assets/fonts/`, latin subset, `font-display: swap`, system-ui fallback. The shop machine must render correctly with zero network.
*Rejected:* Google Fonts import (offline shop breaks typography); variable font single-file (larger than three static subsets for only three weights).

**D3 — Keep state-based view switching; shell is a layout change only.**
`App.tsx` becomes `grid-template-columns: 236px 1fr` with a `<Sidebar>` (nav config array: group label + [view key, label, optional badge]) and a `<header>` rendering each screen's kicker + title from the same config. The `view` state and screen components' props are untouched.
*Rejected:* React Router adoption (real URLs are nice but orthogonal to this phase; adds redirect/deeplink scope and touches every screen's mount logic — separate change if ever).

**D4 — Keep current screen names in the nav (Till, Receiving, Unpacking…), not the mockup's names (Register, Pallet intake…).**
Operators know the current words; phase 1 changes how the app looks, not its vocabulary. Grouping: Operations (Receiving, Unpacking, Prep, Pricing, Lots, Review) · Selling (Till, Reprint) · Back office (Catalog, Suppliers, Printer config). Capture remains phone-only and hidden from the sidebar.
*Rejected:* adopting mockup vocabulary now (retraining cost bundled into a visual change; rename is reversible later at near-zero cost).

**D5 — Responsive: sidebar hides at ≤760px behind a hamburger drawer; phone screens keep today's stacked layouts under the new tokens.**
The existing `≤760px hide nav` and `≤900px unpacking stack` breakpoints are preserved; only their appearance changes.
*Rejected:* desktop-only reskin (leaves Capture/phone Unpacking in the old language — two-system state again).

**D6 — One red accent everywhere; the till's teal/coral identity is removed.**
Status colors map to the DS ramps: good → neutral-800 text on neutral-100 tag, warn/stop → accent ramp (`--color-accent-700` for danger text). Where the old UI leaned on green-vs-red meaning (condition tags, reconciliation deltas), meaning is preserved with tag styles from the DS (`tag-neutral`, `tag-accent`, `tag-accent-2`, `tag-outline`) plus wording — the design's approach (e.g. "+2 over" as a tag, not a green pill).
*Rejected:* keeping per-screen accent identities (the mixed look is the disease being treated).

**D7 — Playwright boots the real stack: Spring Boot on port 8081 with an `e2e` profile + scratch SQLite, and Vite dev server proxying to it.**
- `e2e` Spring profile: `spring.datasource.url` points at a scratch file under `backend/target/e2e/`; Flyway runs all migrations; seed data loads after Flyway (deterministic UUIDs — supplier, lot, boxes, products across states, one completed sale, printer config pointing at an unroutable address).
- The label-print poller and any printer sends are disabled in the `e2e` profile — tests must never emit TSPL to a real device (see memory: a dev DB once clobbered the shop printer address; e2e must be incapable of printing).
- Playwright `webServer` array starts backend then Vite (`VITE_BACKEND=http://localhost:8081`); generous backend `timeout` for JVM boot.
- Suite layout: `e2e/screens/<screen>.spec.ts`, shared fixture constants in `e2e/seed.ts` mirroring the SQL fixture's IDs/barcodes.
*Rejected:* mocked-API tests (user chose real backend; contract drift is exactly what smoke tests must catch); pre-built committed SQLite file (opaque binary in git, drifts from migrations; SQL seed re-applied on fresh migrate stays honest).

**D8 — Commit cadence: foundation (tokens+fonts+components) → shell → one commit per screen → harness+tests.**
Keeps every commit reviewable line-by-line and bisectable if a screen regresses.

## Risks / Trade-offs

- [All twelve screens change appearance at once] → smoke suite lands in the same change; per-screen commits allow bisecting; final manual walk-through of every screen before PR.
- [Class-name collisions: DS classes like `.card`, `.btn` may already exist with different rules] → `styles.css` is rewritten as one file, so each class has exactly one definition; sweep verifies every usage site against the new definition.
- [JVM boot time makes e2e slow or flaky] → single backend boot for the whole suite (workers=1 or shared server), `webServer.timeout` ≥ 120s, scratch DB recreated per run not per test.
- [Seed fixture drifts from future migrations] → seed runs on a freshly migrated DB every run, so a breaking migration fails loudly in e2e, which is the desired signal.
- [`color-mix()` and `:has()` in DS CSS need modern browsers] → dashboard runs on the shop's current Chrome/Edge; both features are supported there; no polyfill work.
- [Archivo woff2 adds ~90KB to the bundle assets] → acceptable; assets load from local disk at the shop.

## Migration Plan

1. Land foundation commits behind no flag — each commit keeps the app building and usable.
2. `npm run e2e` green locally is the merge gate; PR to main as usual.
3. Deploy = existing jar-swap flow (dashboard is served by the backend jar); no DB or config migration. Rollback = previous jar.

## Open Questions

- None blocking. Sidebar badge counts (e.g. open lots) are listed in the design mockup but need endpoints that exist per-screen today; phase 1 ships static labels without counts, later phases wire badges when their endpoints arrive.
