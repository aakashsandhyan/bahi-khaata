# Dashboard Smoke Tests

## MODIFIED Requirements

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
