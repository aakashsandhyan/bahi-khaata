# Receiving Progress

## ADDED Requirements

### Requirement: Progress counter displays box counts
The receiving detail view SHALL display a real-time progress counter showing how many boxes have reached terminal or in-progress states versus the total expected.

#### Scenario: Counter displays on detail view
- **WHEN** user has selected a lot and the detail view opens
- **THEN** system displays a prominent progress counter showing "X / Y boxes" (X = received + unpacked + rejected + notReceived, Y = total expected)

#### Scenario: Counter updates after each action
- **WHEN** user receives a box, marks it not-received, or marks it damaged
- **THEN** system immediately updates the progress counter to reflect the new count

#### Scenario: Counter shows all state counts
- **WHEN** user views the detail screen for a lot
- **THEN** system displays detailed breakdown: expected, received, unpacked, rejected, notReceived (available from GET /api/lots/{lotId}/boxes response)
