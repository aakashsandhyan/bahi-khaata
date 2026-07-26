# Lot Picker

## ADDED Requirements

### Requirement: User can select a lot from a list
The web dashboard receiving screen SHALL allow users to select an open lot from a visual list instead of manually typing a UUID.

#### Scenario: List renders on screen load
- **WHEN** user navigates to the Receiving tab with no lot selected
- **THEN** system renders an overview list of all open lots (receivingComplete == false), sorted by incomplete-receiving first, then by created date (newest first)

#### Scenario: Each lot shows progress
- **WHEN** a lot is displayed in the overview
- **THEN** system shows: supplier name, received date, box progress bar (received + unpacked + rejected + notReceived out of expected)

#### Scenario: Tapping a lot opens detail view
- **WHEN** user taps a lot card in the overview
- **THEN** system opens the receiving detail view for that lot (lot selector is no longer visible; carton scan field takes focus)

### Requirement: Backend provides lot list with counts
The backend SHALL expose a GET endpoint to list all open lots with box counts per state.

#### Scenario: List endpoint returns open lots only
- **WHEN** client calls `GET /api/lots`
- **THEN** system returns a JSON array of LotSummaryDto objects (id, supplier, receivedOn, receivingComplete, expected, received, unpacked, rejected, notReceived) for all lots where `lot.isOpen()` == true

#### Scenario: Box counts are accurate
- **WHEN** client calls `GET /api/lots` for a lot with 3 received, 1 unpacked, 2 rejected, 1 not-received, 0 expected boxes
- **THEN** response includes received=3, unpacked=1, rejected=2, notReceived=1, expected=7 (total of all states)

#### Scenario: Lots are sorted by completion status
- **WHEN** client calls `GET /api/lots` and there are both incomplete and complete lots in the system
- **THEN** system returns incomplete lots (receivingComplete == false) first, sorted by createdAt descending (newest first), followed by complete lots
