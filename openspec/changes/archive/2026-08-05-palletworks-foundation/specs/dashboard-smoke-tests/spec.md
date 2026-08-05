## ADDED Requirements

### Requirement: Real-stack test harness
The repository SHALL provide a Playwright end-to-end harness under `dashboard/web/e2e/` that starts the real Spring Boot backend with a dedicated `e2e` profile (scratch SQLite database file, all Flyway migrations applied, listening on a port that cannot collide with a normally running dev backend) and the Vite dev server pointed at it, via Playwright's managed `webServer` configuration. The whole suite SHALL run with a single command (`npm run e2e`).

#### Scenario: One command runs the suite
- **WHEN** `npm run e2e` is executed in `dashboard/web` on a machine with no servers already running
- **THEN** the backend and frontend start automatically, all tests execute against them, and the processes shut down afterwards

### Requirement: Deterministic seed fixture
The `e2e` profile SHALL load a deterministic seed dataset into the freshly migrated scratch database before tests run: fixed UUIDs and barcodes covering at least one supplier, one lot with boxes, products in each major lifecycle state (received, counted, priced, needs-work), and one completed sale. Test code SHALL reference these fixtures through shared constants, not string literals scattered across tests. Re-running the suite SHALL always start from an identical database state.

#### Scenario: Reruns are identical
- **WHEN** the suite is run twice in a row
- **THEN** both runs start from byte-equivalent seed state and no test depends on data left over from a previous run

#### Scenario: Migration breakage surfaces in e2e
- **WHEN** a future schema migration breaks the seed fixture
- **THEN** the suite fails at seed time with an explicit error, before any test runs

### Requirement: Per-screen smoke coverage
The suite SHALL contain at least one smoke test for every dashboard screen (Till, Receiving, Unpacking, Prep, Pricing, Lots, Capture, Review, Reprint, Catalog, Suppliers, Printer config), each asserting that the screen renders its key elements from seeded backend data and that one core interaction on that screen completes successfully (e.g. Till: keying a seeded barcode adds a cart line; Receiving: opening the seeded lot lists its boxes).

#### Scenario: Every screen has a smoke test
- **WHEN** the e2e suite is inspected
- **THEN** each of the twelve screens has at least one spec exercising render plus one interaction against the real backend

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
