# Box Receipt Creation

## MODIFIED Requirements

### Requirement: Consignment import creates BoxReceipt rows
When a consignment is imported, the system SHALL create one BoxReceipt row for each distinct tracking number in the lot's lines, in EXPECTED state.

#### Scenario: BoxReceipt rows created on import
- **WHEN** client calls `POST /api/consignments/import` with a lot containing multiple ImportLine entries with distinct trackingNumbers
- **THEN** system creates: one Lot, one Box per distinct trackingNumber, one BoxReceipt per distinct trackingNumber (in EXPECTED state), and one ExpectedLine per ImportLine

#### Scenario: BoxReceipt state is EXPECTED
- **WHEN** a consignment is imported
- **THEN** all created BoxReceipt rows have box_state = 'EXPECTED' and can be transitioned via ReceivingService methods

#### Scenario: BoxReceipt and Box are created together
- **WHEN** a new trackingNumber is encountered during import
- **THEN** system creates both a Box (existing behavior) and a BoxReceipt (new behavior) in the same transaction; both reference the same lot_id and tracking number

#### Scenario: One BoxReceipt per tracking number
- **WHEN** multiple ImportLine entries have the same trackingNumber in a single lot
- **THEN** system creates exactly one BoxReceipt for that trackingNumber (not one per line); BoxReceipt is created on first occurrence of the tracking number
