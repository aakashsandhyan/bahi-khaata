# dashboard-shell — Delta Spec

## ADDED Requirements

### Requirement: Screens are reached through grouped navigation

The dashboard SHALL present its screens in named groups — Checkout (Till), Inventory (Lots, Receiving, Unpacking, Prep, Catalog), Pricing & Labels (Pricing, Review, Reprint), and Configuration (Printer, Suppliers) — rather than a single flat row. The grouping SHALL be defined in one data structure that maps groups to screens, so later changes (such as role-based hiding) can filter it in one place.

#### Scenario: A desktop user navigates by group
- **WHEN** the dashboard is opened on a desktop-width window
- **THEN** a sidebar lists the four groups with their screens, and choosing a screen shows it in the content area

#### Scenario: Suppliers lives under Configuration
- **WHEN** a user looks for the Suppliers screen
- **THEN** it is found under the Configuration group, not among the shop-floor inventory screens

### Requirement: The Till takes the screen when active

Opening the Till SHALL collapse the navigation to an icon rail so the scan-and-sell flow owns the width; navigation SHALL remain one tap/click away.

#### Scenario: Till focus mode
- **WHEN** the Till screen is selected
- **THEN** the sidebar collapses to icons and the Till fills the remaining width
- **AND** selecting any other screen restores the expanded sidebar

### Requirement: Phones navigate by bottom tab bar

On phone-width viewports the dashboard SHALL show a bottom tab bar of operator screens with touch-sized targets (minimum 16px text and 12px padding), instead of hiding navigation entirely. The `#capture` hash entry point SHALL continue to open the capture screen directly.

#### Scenario: Phone shows operator tabs
- **WHEN** the dashboard is opened on a phone-width viewport
- **THEN** a bottom tab bar offers the operator screens (Unpacking, Capture, Review) and no desktop sidebar is shown

#### Scenario: Capture hash still lands on capture
- **WHEN** a phone opens the dashboard with `#capture` in the URL
- **THEN** the capture screen is shown, as before

### Requirement: Existing screens are unchanged by the shell

Adopting the shell SHALL NOT alter any screen's own content or behavior: each existing screen SHALL render inside the shell as it did under the old top bar, and screens unrouted before the shell (dead code) SHALL be removed rather than carried.

#### Scenario: A screen renders as before
- **WHEN** any routed screen is opened through the new navigation
- **THEN** its content and behavior match what the flat top bar showed

#### Scenario: Dead screens are gone
- **WHEN** the codebase is searched for the previously unrouted Pricing and BulkPrint components
- **THEN** they no longer exist
