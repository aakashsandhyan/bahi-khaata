# Receiving Workflow

## MODIFIED Requirements

### Requirement: Web receiving screen uses shared architecture
The web dashboard Receiving.tsx component SHALL adopt the same architecture and patterns as other dashboard screens (shared api.ts, shared styles.css, Unpacking.tsx-style navigation).

#### Scenario: Component uses shared API client
- **WHEN** Receiving.tsx makes HTTP requests
- **THEN** system uses `api.receiving.*` methods (not raw `fetch` calls); requests route through the shared API client with unified error handling via BackendError

#### Scenario: Component uses shared styles
- **WHEN** Receiving.tsx renders UI elements
- **THEN** system uses classes from shared `styles.css` (`.receiving`, `.ov`, `.ov-bar`, `.scan`, `.flag`, `.banner`, `.btn-primary`, `.actions`) instead of private inline styles

#### Scenario: Component navigates overview to detail
- **WHEN** user first opens the Receiving tab
- **THEN** system displays an overview of all open lots (rendered with shared `.ov` card classes) with progress bars; tapping a lot opens the detail view

#### Scenario: Detail view allows scanning and exceptions
- **WHEN** user has a lot selected in the detail view
- **THEN** system displays: lot supplier name, progress counter, autofocused `.scan` input for carton ID, buttons for "Not Received" and "Damaged", a list of all boxes with state badges

#### Scenario: Done button returns to overview
- **WHEN** user clicks the Done button in the detail view
- **THEN** system clears the selected lot, refreshes the lot list in the overview, and returns to the overview; Done button is only available when all boxes are terminal (allTerminal == true) or user clicks despite incomplete

#### Scenario: State badges color-code box states
- **WHEN** the box list is rendered
- **THEN** each box shows a `.flag` badge: flag ok (green) for RECEIVED/UNPACKING/UNPACKED, flag stop (red) for REJECTED/NOT_RECEIVED, flag neutral (gray) for EXPECTED
