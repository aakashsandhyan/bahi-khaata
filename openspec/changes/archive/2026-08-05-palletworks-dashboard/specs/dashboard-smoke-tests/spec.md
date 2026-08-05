## MODIFIED Requirements

### Requirement: Deterministic seed fixture
The `e2e` profile SHALL load a deterministic seed dataset into the freshly migrated scratch database before tests run: fixed UUIDs and barcodes covering at least one supplier, one lot with boxes, products in each major lifecycle state (received, counted, priced, needs-work), and completed sales. The seed SHALL include `sale` rows (with their `sale_line` rows) whose amounts are fixed constants and whose `created_at` timestamps are stamped at apply time (via `strftime('%Y-%m-%dT%H:%M:%S.000Z','now')`) so they land inside today's IST window on every run; the timestamps SHALL be ISO-8601 UTC text with a trailing `Z` and fixed-width millis so they match the app's string range filter. Test code SHALL reference these fixtures through shared constants, not string literals scattered across tests. Determinism means every run starts from identical seed amounts and structure, with only the today-relative sale timestamps moving with the run day; re-running the suite SHALL always produce the same asserted figures.

#### Scenario: Reruns are structurally identical
- **WHEN** the suite is run twice in a row
- **THEN** both runs start from identical seed amounts and structure — including the fixed sale amounts — and no test depends on data left over from a previous run

#### Scenario: Seeded sales land in today's window
- **WHEN** the seed is applied and the suite runs on any calendar day
- **THEN** the seeded `sale` rows carry ISO-8601-Z `created_at` timestamps inside today's IST window, so the revenue-today tile shows the fixed seeded amounts

#### Scenario: Migration breakage surfaces in e2e
- **WHEN** a future schema migration breaks the seed fixture
- **THEN** the suite fails at seed time with an explicit error, before any test runs

### Requirement: Per-screen smoke coverage
The suite SHALL contain at least one smoke test for every dashboard screen (Dashboard, Till, Sales, Receiving, Unpacking, Prep, Pricing, Lots, Capture, Review, Reprint, Catalog, Suppliers, Printer config, Receipt printer, Bill settings), each asserting that the screen renders its key elements from seeded backend data and that one core interaction on that screen completes successfully (e.g. Till: keying a seeded barcode adds a cart line; Receiving: opening the seeded lot lists its boxes; Dashboard: the revenue-today tile shows the seeded amount and an alert row navigates to its owning screen).

#### Scenario: Every screen has a smoke test
- **WHEN** the e2e suite is inspected
- **THEN** each of the sixteen screens has at least one spec exercising render plus one interaction against the real backend
