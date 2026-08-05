## MODIFIED Requirements

### Requirement: Grouped sidebar navigation
On viewports wider than 760px the dashboard SHALL render a 236px fixed-width, full-height, sticky sidebar containing: a brand block (shop name and sub-line), navigation entries grouped under uppercase group labels — Operations (Dashboard, Receiving, Unpacking, Prep, Pricing, Lots, Review, Inventory), Selling (Till, Sales, Reprint), Back office (Catalog, Suppliers, Printer config, Receipt printer, Bill settings) — and a footer identifying the signed-in operator (from the locally stored operator name) when one is set. Dashboard SHALL be the first entry of the Operations group and SHALL carry the kicker "Overview". Inventory SHALL be the last entry of the Operations group, immediately after Review. The active entry SHALL be visually marked (inverted background and accent left bar). Screen names SHALL keep the app's current vocabulary.

#### Scenario: Navigate between screens
- **WHEN** the user clicks a sidebar entry
- **THEN** the corresponding screen renders in the main area and that entry shows the active treatment

#### Scenario: Operator shown when known
- **WHEN** an operator name is stored locally
- **THEN** the sidebar footer displays it

#### Scenario: Dashboard leads the Operations group
- **WHEN** the sidebar renders on a desktop viewport
- **THEN** Dashboard appears as the first entry under Operations with the kicker "Overview"

#### Scenario: Inventory closes the Operations group
- **WHEN** the sidebar renders on a desktop viewport
- **THEN** Inventory appears as the last entry under Operations, immediately after Review
