# Box State List

## ADDED Requirements

### Requirement: User can view all boxes for a lot with their states
The receiving detail view SHALL display a list of all boxes for the selected lot, showing each box's current state and timestamp.

#### Scenario: Box list renders below scan input
- **WHEN** user has selected a lot and the detail view is open
- **THEN** system displays a scrollable list of all boxes (from GET /api/lots/{lotId}/boxes response) below the carton scan input

#### Scenario: Each box shows state with visual badge
- **WHEN** a box is displayed in the list
- **THEN** system shows: manifestCartonId, state badge (color-coded: ok for RECEIVED/UNPACKING/UNPACKED, stop for REJECTED/NOT_RECEIVED, neutral for EXPECTED), and receivedAt timestamp if available

#### Scenario: State badges are visually distinct
- **WHEN** state badges are rendered in the box list
- **THEN** system uses: flag ok (green) for RECEIVED/UNPACKING/UNPACKED, flag stop (red) for REJECTED/NOT_RECEIVED, flag neutral (gray) for EXPECTED

#### Scenario: List reflects changes immediately
- **WHEN** user scans a carton and system receives it
- **THEN** that box's entry in the list updates from EXPECTED to RECEIVED with a receivedAt timestamp without requiring page refresh
