# Mark Not Received Endpoint

## ADDED Requirements

### Requirement: Backend exposes mark-not-received endpoint
The backend SHALL provide a dedicated HTTP POST endpoint to transition a box to NOT_RECEIVED state, distinct from the reject endpoint.

#### Scenario: Endpoint accepts correct request
- **WHEN** client calls `POST /api/lots/{lotId}/mark-not-received` with body `{manifestCartonId: "BOX-001"}`
- **THEN** system responds with HTTP 200 and a JSON object containing: state (string "NOT_RECEIVED"), rejectedReason (null)

#### Scenario: State transitions correctly
- **WHEN** a box in EXPECTED state receives a mark-not-received request
- **THEN** system transitions the box to NOT_RECEIVED state and sets receivingComplete flag on the lot if all boxes are now terminal

#### Scenario: Error handling
- **WHEN** client calls mark-not-received for a lot that does not exist
- **THEN** system responds with HTTP 404 and error message "lot <uuid>"
- **WHEN** client calls mark-not-received for a manifestCartonId not in the manifest
- **THEN** system responds with HTTP 400 and error message "box <id> not in manifest for lot <uuid>"
- **WHEN** client calls mark-not-received for a box not in EXPECTED state
- **THEN** system responds with HTTP 400 and error message "box <id> must be EXPECTED to mark not received, but is <current-state>"

#### Scenario: Box remains untrackable after marking not-received
- **WHEN** a box is marked NOT_RECEIVED
- **THEN** system sets state to NOT_RECEIVED (never removes the BoxReceipt row); cost allocation treats it as zero contribution to the lot
