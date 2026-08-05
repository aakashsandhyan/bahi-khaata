## MODIFIED Requirements

### Requirement: Grouped sidebar navigation
On viewports wider than 760px the dashboard SHALL render a 236px fixed-width, full-height, sticky sidebar containing: a brand block (shop name and sub-line), navigation entries grouped under uppercase group labels — Operations (Dashboard, Receiving, Unpacking, Prep, Pricing, Lots, Review, Inventory), Selling (Sales, Reprint), Back office (Suppliers, Settings) — and a footer identifying the signed-in operator (from the locally stored operator name) when one is set. The sidebar SHALL list exactly twelve entries across the three groups. Dashboard SHALL be the first entry of the Operations group and SHALL carry the kicker "Overview". Inventory SHALL be the last entry of the Operations group, immediately after Review. The single Settings entry SHALL replace the three former admin entries (Printer config, Receipt printer, Bill settings) and the former Catalog entry SHALL NOT appear. The active entry SHALL be visually marked (inverted background and accent left bar). Screen names SHALL keep the app's current vocabulary.

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

#### Scenario: Settings replaces the three admin entries
- **WHEN** the sidebar renders on a desktop viewport
- **THEN** the Back office group lists Suppliers and a single Settings entry, and no separate Printer config, Receipt printer, or Bill settings entry is present

#### Scenario: Catalog is absent and the sidebar lists twelve entries
- **WHEN** the sidebar renders on a desktop viewport
- **THEN** no Catalog entry is present and exactly twelve navigation entries appear across Operations, Selling, and Back office

### Requirement: Capture stays phone-only
The Capture screen SHALL remain reachable on phone viewports as today and SHALL NOT appear in the desktop sidebar navigation. The Till screen SHALL likewise be unlisted: it SHALL NOT appear in the desktop sidebar and SHALL stay in code, reachable only by the `#till` hash. At load, both `#till` and `#capture` SHALL resolve on any viewport — `#till` SHALL land on the Till screen and `#capture` SHALL land on the Capture screen — using the same param-less state-switch mechanism, with no `hashchange` listener (the hash is consulted initial-only). Neither hash-reachable screen SHALL be added to the sidebar.

#### Scenario: Desktop sidebar omits Capture
- **WHEN** the sidebar renders on a desktop viewport
- **THEN** no Capture entry is present, and the Capture screen remains reachable on phones exactly as before this change

#### Scenario: Desktop sidebar omits Till
- **WHEN** the sidebar renders on a desktop viewport
- **THEN** no Till entry is present, and the Till screen stays in code reachable only via the `#till` hash

#### Scenario: Till hash resolves on any viewport at load
- **WHEN** the app opens with the `#till` hash on any viewport
- **THEN** the Till screen is shown, resolved once at load with no `hashchange` listener

#### Scenario: Capture hash resolves on any viewport at load
- **WHEN** the app opens with the `#capture` hash on any viewport
- **THEN** the Capture screen is shown, resolved once at load with no `hashchange` listener
