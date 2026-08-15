## MODIFIED Requirements

### Requirement: Per-screen smoke coverage
The suite SHALL contain at least one smoke test for every listed dashboard screen — the twelve sidebar entries: Dashboard, Receiving, Unpacking, Prep, Pricing, Lots, Review, Inventory, Sales, Reprint, Suppliers, and Settings — plus the two unlisted, hash-reachable screens Till (via `#till`) and Capture (via `#capture`), plus the Item detail view opened from an Inventory row. Each SHALL assert that the screen renders its key elements from seeded backend data and that one core interaction on it completes successfully (e.g. Till: keying a seeded barcode adds a cart line; Receiving: opening the seeded lot lists its boxes; Dashboard: the revenue-today tile shows the seeded amount and an alert row navigates to its owning screen; Inventory: applying a filter narrows the filtered-set totals in the On floor scope; Item detail: the movement log, price history, and per-batch sections all render for a seeded product and a bin edit persists across reload). The Settings smoke SHALL exercise each of its three tabs — Label printer, Receipt printer, and Bill — asserting each mounts its component and shows its freshly-fetched saved values. The former standalone Catalog smoke SHALL be replaced by an on-paper-scope smoke that exercises Inventory's On paper scope (products known from a manifest but never counted are listed via the catalog browse API with catalog columns and no fabricated stock zeros). No separate Catalog, Printer config, Receipt printer, or Bill settings screen smoke SHALL remain.

#### Scenario: Every listed and hash-reachable screen has a smoke test
- **WHEN** the e2e suite is inspected
- **THEN** each of the twelve listed screens, the two hash-reachable screens (Till via `#till`, Capture via `#capture`), and the Item detail view has at least one spec exercising render plus one interaction against the real backend

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
