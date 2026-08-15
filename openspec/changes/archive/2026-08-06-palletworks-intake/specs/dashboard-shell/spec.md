# Dashboard Shell

## MODIFIED Requirements

### Requirement: Grouped sidebar navigation
On viewports wider than 760px the dashboard SHALL render a 236px fixed-width, full-height, sticky sidebar containing: a brand block (shop name and sub-line), navigation entries grouped under uppercase group labels — Operations (Dashboard, Intake, Unpacking, Prep, Pricing, Review, Inventory), Selling (Sales, Reprint), Back office (Suppliers, Settings) — and a footer identifying the signed-in operator (from the locally stored operator name) when one is set. The sidebar SHALL list exactly eleven entries across the three groups, in this order: Dashboard, Intake, Unpacking, Prep, Pricing, Review, Inventory, Sales, Reprint, Suppliers, Settings. Dashboard SHALL be the first entry of the Operations group and SHALL carry the kicker "Overview". Intake SHALL be the second entry of the Operations group, immediately after Dashboard, in the slot the former Receiving entry held; it SHALL replace both the former Receiving and the former Lots entries, and neither Receiving nor Lots SHALL appear. Inventory SHALL be the last entry of the Operations group, immediately after Review. The single Settings entry SHALL replace the three former admin entries (Printer config, Receipt printer, Bill settings) and the former Catalog entry SHALL NOT appear. The active entry SHALL be visually marked (inverted background and accent left bar). Screen names SHALL keep the app's current vocabulary.

#### Scenario: Navigate between screens
- **WHEN** the user clicks a sidebar entry
- **THEN** the corresponding screen renders in the main area and that entry shows the active treatment

#### Scenario: Operator shown when known
- **WHEN** an operator name is stored locally
- **THEN** the sidebar footer displays it

#### Scenario: Dashboard leads the Operations group
- **WHEN** the sidebar renders on a desktop viewport
- **THEN** Dashboard appears as the first entry under Operations with the kicker "Overview"

#### Scenario: Intake sits second, replacing Receiving and Lots
- **WHEN** the sidebar renders on a desktop viewport
- **THEN** Intake appears as the second Operations entry immediately after Dashboard, and no separate Receiving or Lots entry is present

#### Scenario: Inventory closes the Operations group
- **WHEN** the sidebar renders on a desktop viewport
- **THEN** Inventory appears as the last entry under Operations, immediately after Review

#### Scenario: Settings replaces the three admin entries
- **WHEN** the sidebar renders on a desktop viewport
- **THEN** the Back office group lists Suppliers and a single Settings entry, and no separate Printer config, Receipt printer, or Bill settings entry is present

#### Scenario: Catalog is absent and the sidebar lists eleven entries
- **WHEN** the sidebar renders on a desktop viewport
- **THEN** no Catalog entry is present and exactly eleven navigation entries appear across Operations, Selling, and Back office
