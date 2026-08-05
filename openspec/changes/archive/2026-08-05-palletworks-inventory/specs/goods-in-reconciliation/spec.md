## ADDED Requirements

### Requirement: Manual lots are marked received by hand
A lot with no manifest-backed boxes has no event that can complete its receiving automatically, so the system SHALL provide an explicit action to mark such a lot's receiving finished. The action SHALL be available for any open lot whose receiving is not yet complete, SHALL be recorded as the same `receiving_complete` fact the automatic path sets, and SHALL NOT close the lot or alter its stock. The dashboard's still-receiving alert therefore always points at lots an operator can actually act on.

#### Scenario: Marking a manual lot finished
- **WHEN** an operator uses the receiving-finished action on an open manual lot
- **THEN** the lot's `receiving_complete` becomes true and it leaves the dashboard's still-receiving alert

#### Scenario: Manifest lots still complete automatically
- **WHEN** the last manifest-backed box of a lot reaches a terminal state
- **THEN** receiving completes exactly as before, with no manual action required
