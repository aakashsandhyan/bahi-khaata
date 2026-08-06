## Purpose

End-to-end smoke coverage of the dashboard against the real backend with a deterministic seed; the suite is the merge and deploy gate. Established by the palletworks-foundation change.

## Requirements

### Requirement: Real-stack test harness
The repository SHALL provide a Playwright end-to-end harness under `dashboard/web/e2e/` that starts the real Spring Boot backend with a dedicated `e2e` profile (scratch SQLite database file, all Flyway migrations applied, listening on a port that cannot collide with a normally running dev backend) and the Vite dev server pointed at it, via Playwright's managed `webServer` configuration. The whole suite SHALL run with a single command (`npm run e2e`).

#### Scenario: One command runs the suite
- **WHEN** `npm run e2e` is executed in `dashboard/web` on a machine with no servers already running
- **THEN** the backend and frontend start automatically, all tests execute against them, and the processes shut down afterwards

### Requirement: Deterministic seed fixture
The `e2e` profile SHALL load a deterministic seed dataset into the freshly migrated scratch database before tests run: fixed UUIDs and barcodes covering at least one supplier, one lot with boxes, products in each major lifecycle state (received, counted, priced, needs-work), and completed sales. The seed SHALL assign a fixed `bin` to at least one batch and leave at least one batch with no bin, so bin display, filtering, and the em-dash rendering can be asserted; it SHALL also include at least one `price_history` row for a priced product with a fixed old price and new price, so the item detail price history and the price-change journal can be asserted against known values. The seed SHALL include `sale` rows (with their `sale_line` rows) whose amounts are fixed constants and whose `created_at` timestamps are stamped at apply time (via `strftime('%Y-%m-%dT%H:%M:%S.000Z','now')`) so they land inside today's IST window on every run; the timestamps SHALL be ISO-8601 UTC text with a trailing `Z` and fixed-width millis so they match the app's string range filter. Test code SHALL reference these fixtures through shared constants, not string literals scattered across tests. Determinism means every run starts from identical seed amounts and structure, with only the today-relative sale timestamps moving with the run day; re-running the suite SHALL always produce the same asserted figures.

#### Scenario: Reruns are structurally identical
- **WHEN** the suite is run twice in a row
- **THEN** both runs start from identical seed amounts and structure — including the fixed sale amounts, the seeded bins, and the seeded price-history row — and no test depends on data left over from a previous run

#### Scenario: Seeded sales land in today's window
- **WHEN** the seed is applied and the suite runs on any calendar day
- **THEN** the seeded `sale` rows carry ISO-8601-Z `created_at` timestamps inside today's IST window, so the revenue-today tile shows the fixed seeded amounts

#### Scenario: Seeded bins and price change are present
- **WHEN** the seed is applied
- **THEN** at least one batch carries a fixed bin, at least one batch has no bin, and at least one priced product has a fixed `price_history` row with a known old and new price

#### Scenario: Migration breakage surfaces in e2e
- **WHEN** a future schema migration breaks the seed fixture
- **THEN** the suite fails at seed time with an explicit error, before any test runs

### Requirement: Per-screen smoke coverage
The suite SHALL contain at least one smoke test for every listed dashboard screen — the eleven sidebar entries: Dashboard, Intake, Unpacking, Prep, Pricing, Review, Inventory, Sales, Reprint, Suppliers, and Settings — plus the two unlisted, hash-reachable screens Till (via `#till`) and Capture (via `#capture`), plus the Item detail view opened from an Inventory row. Each SHALL assert that the screen renders its key elements from seeded backend data and that one core interaction on it completes successfully (e.g. Till: keying a seeded barcode adds a cart line; Intake: selecting the seeded lot in the rail drives its header stats, step strip, and tabs, and receiving a box in the Boxes tab transitions it; Dashboard: the revenue-today tile shows the seeded amount and an alert row navigates to its owning screen; Inventory: applying a filter narrows the filtered-set totals in the On floor scope; Item detail: the movement log, price history, and per-batch sections all render for a seeded product and a bin edit persists across reload). The former standalone Receiving and Lots smokes SHALL be replaced by Intake smokes covering: the lot rail with header stats and the inferred step strip, the Boxes receive/not-received/reject flow, the Lines tab's expected/counted/Δ columns, the Reconcile & close tab with the unopened-carton close gate, and manual-lot creation followed by the receiving-finished action. The Settings smoke SHALL exercise each of its three tabs — Label printer, Receipt printer, and Bill — asserting each mounts its component and shows its freshly-fetched saved values. The former standalone Catalog smoke SHALL be replaced by an on-paper-scope smoke that exercises Inventory's On paper scope (products known from a manifest but never counted are listed via the catalog browse API with catalog columns and no fabricated stock zeros). No separate Receiving, Lots, Catalog, Printer config, Receipt printer, or Bill settings screen smoke SHALL remain.

#### Scenario: Every listed and hash-reachable screen has a smoke test
- **WHEN** the e2e suite is inspected
- **THEN** each of the eleven listed screens, the two hash-reachable screens (Till via `#till`, Capture via `#capture`), and the Item detail view has at least one spec exercising render plus one interaction against the real backend

#### Scenario: Intake smoke replaces the Receiving and Lots smokes
- **WHEN** the Intake smoke runs against the seeded backend
- **THEN** it selects the seeded lot in the rail and asserts the header stats, step strip, Boxes flow, Lines Δ column, and the Reconcile & close unopened-carton gate, and it creates a manual lot and runs the receiving-finished action; no standalone Receiving or Lots smoke exists

#### Scenario: Till and Capture are smoked via their hashes
- **WHEN** the Till and Capture smokes run
- **THEN** each screen is reached by loading its hash (`#till`, `#capture`) rather than a sidebar entry, and its core interaction completes against the seeded backend

#### Scenario: Settings tabs are each exercised
- **WHEN** the Settings smoke runs
- **THEN** each of the Label printer, Receipt printer, and Bill tabs is opened and asserted to mount its component and render its saved values

#### Scenario: On-paper scope smoke replaces the Catalog smoke
- **WHEN** the on-paper-scope smoke runs against the seeded backend
- **THEN** Inventory's On paper scope lists manifest-known, uncounted products via the catalog browse API with catalog columns and no fabricated stock zeros, and no standalone Catalog screen smoke exists

#### Scenario: Inventory smoke asserts filtered totals
- **WHEN** the Inventory smoke runs against the seeded backend in the On floor scope
- **THEN** the table renders its rows and applying a filter narrows the filtered-set totals (units, cost, retail value)

#### Scenario: Item detail smoke asserts all three sections and a persisted bin edit
- **WHEN** the Item detail smoke opens a seeded product from an Inventory row
- **THEN** the movement log, price history, and per-batch sections all render, and a bin edit made on a batch persists across a reload

### Requirement: Printing is impossible under test
The e2e run MUST make reaching a physical printer impossible through both available layers: the seeded printer configuration MUST have `enabled = 0` (the label-send driver refuses before opening any socket, so queued jobs are never transmitted) AND MUST point at a guaranteed-unroutable address (RFC 5737 TEST-NET), because the admin "Test Print" connectivity check is a separate code path that does not consult the enabled flag. No print job may be seeded in `queued` status.

#### Scenario: Label sends refuse without connecting
- **WHEN** a test exercises a screen that can queue labels
- **THEN** the send driver rejects on the disabled flag and no connection to any printer address is made

#### Scenario: Connectivity test cannot reach a device
- **WHEN** a test exercises the printer connectivity check
- **THEN** the only address it can try is TEST-NET-unroutable, and the check times out without reaching any device

### Requirement: Suite is the pre-deploy gate
The smoke suite SHALL be treated as a merge and deploy gate: a red suite blocks merging the change and blocks the jar-swap deploy flow.

#### Scenario: Red suite blocks deploy
- **WHEN** any smoke test fails
- **THEN** the deploy checklist stops until the suite is green
