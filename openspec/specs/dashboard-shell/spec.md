## Purpose

The dashboard application frame: grouped sidebar navigation, per-screen header, operator identity, responsive collapse, and screen visibility rules. Established by the palletworks-foundation change.

## Requirements

### Requirement: Grouped sidebar navigation
On viewports wider than 760px the dashboard SHALL render a 236px fixed-width, full-height, sticky sidebar containing: a brand block (shop name and sub-line), navigation entries grouped under uppercase group labels — Operations (Dashboard, Receiving, Unpacking, Prep, Pricing, Lots, Review), Selling (Till, Sales, Reprint), Back office (Catalog, Suppliers, Printer config, Receipt printer, Bill settings) — and a footer identifying the signed-in operator (from the locally stored operator name) when one is set. Dashboard SHALL be the first entry of the Operations group and SHALL carry the kicker "Overview". The active entry SHALL be visually marked (inverted background and accent left bar). Screen names SHALL keep the app's current vocabulary.

#### Scenario: Navigate between screens
- **WHEN** the user clicks a sidebar entry
- **THEN** the corresponding screen renders in the main area and that entry shows the active treatment

#### Scenario: Operator shown when known
- **WHEN** an operator name is stored locally
- **THEN** the sidebar footer displays it

#### Scenario: Dashboard leads the Operations group
- **WHEN** the sidebar renders on a desktop viewport
- **THEN** Dashboard appears as the first entry under Operations with the kicker "Overview"

### Requirement: Per-screen header
The main area SHALL render a sticky header for every screen showing an uppercase accent-colored kicker (context line) and the screen title, sourced from the same navigation configuration that drives the sidebar.

#### Scenario: Header follows navigation
- **WHEN** the user switches to any screen
- **THEN** the header shows that screen's kicker and title

### Requirement: Responsive collapse
At viewports of 760px or narrower the sidebar SHALL be hidden by default and reachable via a hamburger control as an overlay drawer; the main content SHALL occupy the full width. Existing phone-oriented layouts (Capture; stacked Unpacking at ≤900px) SHALL retain their current structure, restyled by the shared tokens.

#### Scenario: Phone gets a drawer
- **WHEN** the dashboard is viewed at ≤760px width
- **THEN** no sidebar is visible until the hamburger control opens it as an overlay, and closing it restores full-width content

### Requirement: Capture stays phone-only
The Capture screen SHALL remain reachable on phone viewports as today and SHALL NOT appear in the desktop sidebar navigation.

#### Scenario: Desktop sidebar omits Capture
- **WHEN** the sidebar renders on a desktop viewport
- **THEN** no Capture entry is present, and the Capture screen remains reachable on phones exactly as before this change

### Requirement: View switching behavior is preserved
The shell SHALL preserve the existing state-based view switching in `App.tsx`: no routing library, and no changes to screen component props or mount behavior. The desktop default view SHALL be Dashboard (the landing state changes from Till to Dashboard). Phone landing behavior (Unpacking / Capture) SHALL be unchanged.

#### Scenario: Shell swap is invisible to screens
- **WHEN** any screen is opened via the sidebar
- **THEN** the screen component mounts with the same props and lifecycle as before the shell change

#### Scenario: Desktop lands on Dashboard
- **WHEN** the app opens on a desktop viewport
- **THEN** the Dashboard screen is shown by default

#### Scenario: Phone landing unchanged
- **WHEN** the app opens on a phone viewport
- **THEN** the phone landing behavior (Unpacking / Capture) is exactly as before this change
