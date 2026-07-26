# Receiving Lot List

## ADDED Requirements

### Requirement: Backend exposes GET /api/lots endpoint
The backend SHALL provide an HTTP GET endpoint that returns a list of all open lots with box counts and progress metadata.

#### Scenario: Endpoint returns correct response shape
- **WHEN** client calls `GET /api/lots`
- **THEN** system responds with HTTP 200 and a JSON array of objects containing: id (UUID), supplier (string), receivedOn (ISO-8601 date), receivingComplete (boolean), expected (long), received (long), unpacked (long), rejected (long), notReceived (long)

#### Scenario: Only open lots are returned
- **WHEN** database contains both open lots (state = OPEN) and closed lots (state = CLOSED)
- **THEN** `GET /api/lots` returns only open lots; closed lots are excluded

#### Scenario: Counts are calculated from BoxReceipt states
- **WHEN** a lot has boxes in various states (EXPECTED, RECEIVED, UNPACKING, UNPACKED, REJECTED, NOT_RECEIVED)
- **THEN** response counts each box exactly once: expected = count of all BoxReceipt rows for the lot, received = count where state = RECEIVED, etc.

#### Scenario: Sorting is consistent
- **WHEN** multiple lots are returned in the response
- **THEN** incomplete lots (receivingComplete == false) appear first, sorted by createdAt descending (newest first); complete lots follow in the same order
