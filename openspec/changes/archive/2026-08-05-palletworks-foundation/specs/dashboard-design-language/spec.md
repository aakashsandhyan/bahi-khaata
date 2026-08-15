## ADDED Requirements

### Requirement: Single design-token source
The dashboard SHALL define all colors, spacing, radii, shadows, and font families as CSS custom properties in one stylesheet (`styles.css` `:root` block), using the Palletworks/modernist token set (background `#f3f2f2`, surface `#eae9e9`, ink `#201e1d`, accent `#ec3013`, neutral and accent tonal ramps 100–900, spacing 4/8/12/16/24/32px, all radii `0`). Screen-specific CSS MUST reference these tokens rather than hard-coded values for color, spacing, and radius.

#### Scenario: One definition per token
- **WHEN** the stylesheet is searched for token definitions
- **THEN** each design token is defined exactly once, in the `:root` block

#### Scenario: No stray legacy palette
- **WHEN** the frontend source is searched for the retired palette values (indigo `#4338ca`, the old teal/coral till accents) and retired token names (`--ink`, `--brand`, `--s1`)
- **THEN** no occurrences remain

### Requirement: Offline-safe typography
The dashboard SHALL render all text in Archivo (weights 400, 600, 800) served from self-hosted woff2 files bundled with the app, with a `system-ui` fallback stack. The app MUST NOT request fonts (or any render-critical asset) from an external network host.

#### Scenario: Fonts load without internet
- **WHEN** the dashboard is opened on a machine with no internet access
- **THEN** all screens render with the bundled Archivo files and no external font request is attempted

### Requirement: Square-cornered, divider-drawn surfaces
All dashboard UI elements SHALL render with zero border-radius, and section separation SHALL use 2px dividers at the token divider color (1px for row-level separation inside tables and lists).

#### Scenario: No rounded corners
- **WHEN** any screen is rendered
- **THEN** no element displays a border-radius greater than 0 (radio dots excepted, which are circles by definition)

### Requirement: Single accent identity
The dashboard SHALL use one accent color (`#ec3013` and its ramp) for primary actions, active states, kickers, and emphasis across every screen. Per-screen accent identities MUST NOT exist. Status meaning (matched/short/over, condition grades, danger) SHALL be conveyed through the design system's tag variants (`tag-neutral`, `tag-accent`, `tag-accent-2`, `tag-outline`), the accent-700 danger text color, and explicit wording — not through screen-local color schemes.

#### Scenario: Till uses the shared accent
- **WHEN** the Till (checkout) screen renders its primary actions and highlights
- **THEN** they use the shared accent tokens, and the retired teal/coral values appear nowhere

### Requirement: Shared component classes
The stylesheet SHALL provide the design system's component classes — `btn` (with `btn-primary`, `btn-secondary`, `btn-ghost`, `btn-icon`, `btn-block`), `tag` variants, `seg`/`seg-opt`, `input`, `field`, `table`, `card`, `hr`, and dialog classes — and interactive elements across all screens SHALL use them instead of per-screen one-off button/input/table styling.

#### Scenario: Buttons come from the system
- **WHEN** any screen renders a button
- **THEN** it carries a design-system button class and receives its appearance from the shared definition

### Requirement: Tabular numerals for money and counts
All monetary amounts and count columns SHALL render with `font-variant-numeric: tabular-nums` so digit columns align vertically in tables, carts, and totals.

#### Scenario: Aligned money column
- **WHEN** a table or cart renders a column of rupee amounts
- **THEN** the amounts render with tabular numerals
