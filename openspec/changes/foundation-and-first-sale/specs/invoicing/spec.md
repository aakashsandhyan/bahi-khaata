## ADDED Requirements

### Requirement: An issued invoice is immutable

Once an invoice is issued it SHALL NOT be modified or deleted. The database SHALL reject such attempts rather than relying on application code to prevent them, because Indian GST law does not permit editing the values of an issued tax invoice.

#### Scenario: Updating an issued invoice is rejected at the database

- **WHEN** an update is attempted against an issued invoice or any of its lines, including directly against the database
- **THEN** the database rejects the update
- **AND** the invoice is unchanged

#### Scenario: Deleting an issued invoice is rejected at the database

- **WHEN** a delete is attempted against an issued invoice, including directly against the database
- **THEN** the database rejects the delete
- **AND** the invoice remains present

#### Scenario: An invoice can be completed before it is issued

- **WHEN** an invoice is still being assembled and has not been issued
- **THEN** its contents may be changed

### Requirement: Corrections are recorded as linked reversing entries

A correction, cancellation, or return SHALL be recorded as a new entry that references the invoice it corrects. The original invoice SHALL remain intact and readable, preserving the audit trail.

#### Scenario: A correction references the original

- **WHEN** an issued invoice is corrected
- **THEN** a new entry is created recording the correction
- **AND** that entry references the original invoice

#### Scenario: The original survives the correction

- **WHEN** a correction has been recorded against an invoice
- **THEN** the original invoice remains present and its values are unchanged

### Requirement: Every issued invoice carries the fields GST requires

An issued invoice SHALL record the supplier's legal name, address, and GSTIN; its invoice number and date; the place-of-supply state; an itemised breakdown of each line with its taxable value and tax; and recipient details where the invoice total exceeds ₹50,000. An invoice missing any required field SHALL NOT be issued.

#### Scenario: A complete invoice is issued

- **WHEN** an invoice carrying all required fields is issued
- **THEN** the invoice is issued successfully
- **AND** all those fields are persisted with it

#### Scenario: An incomplete invoice is refused

- **WHEN** issuing is attempted for an invoice missing a required field
- **THEN** the system refuses to issue it
- **AND** reports which field is missing

#### Scenario: Recipient details are required above the threshold

- **WHEN** issuing is attempted for an invoice whose total exceeds ₹50,000 without recipient details
- **THEN** the system refuses to issue it

#### Scenario: Place of supply determines the tax split

- **WHEN** an invoice is issued
- **THEN** the recorded tax breakdown uses CGST and SGST where the place of supply is the supplier's own state, and IGST where it is not

### Requirement: Invoice numbers are consecutive within a financial year

Invoice numbers SHALL be unique and consecutive without gaps within a financial year, SHALL restart at the beginning of each financial year, and SHALL be at most 16 characters using only alphanumerics, hyphens, and slashes. The financial year SHALL be stored on the invoice rather than derived at query time. Number allocation SHALL occur within the same transaction that issues the invoice.

#### Scenario: Numbers are consecutive

- **WHEN** invoices are issued in sequence within a financial year
- **THEN** each is assigned the next number after the previous one, with no gaps

#### Scenario: Numbering restarts each financial year

- **WHEN** the first invoice of a new Indian financial year beginning 1 April is issued
- **THEN** its number restarts the sequence
- **AND** the invoice records the financial year it belongs to

#### Scenario: Numbers satisfy the format constraints

- **WHEN** an invoice number is assigned
- **THEN** it is at most 16 characters and contains only alphanumerics, hyphens, and slashes

#### Scenario: A failed issue consumes no number

- **WHEN** issuing an invoice fails after a number has been allocated
- **THEN** the transaction is rolled back
- **AND** the next successfully issued invoice receives that number, leaving no gap

### Requirement: Totals are rounded once, at issue

Tax and total amounts SHALL be rounded to the nearest rupee under CGST Act §170, with 50 paise and above rounding up. Rounding SHALL be applied once when the invoice is issued, not incrementally as lines accumulate. Both the unrounded computed total and the rounded issued total SHALL be persisted so any difference is auditable.

#### Scenario: Rounding follows the half-up rule

- **WHEN** an invoice total of 50 paise or more above a rupee is rounded
- **THEN** it rounds up to the next rupee
- **AND** a total below 50 paise above a rupee rounds down

#### Scenario: Both totals are retained

- **WHEN** an invoice is issued
- **THEN** the unrounded computed total and the rounded issued total are both persisted

#### Scenario: Line values are not individually rounded

- **WHEN** an invoice with multiple lines is issued
- **THEN** the rounded total is derived from the unrounded sum of its lines, not from a sum of individually rounded lines

### Requirement: Invoice records accommodate future e-invoicing without integration

The invoice record SHALL provide fields for an IRN, a signed QR payload, and a portal acknowledgement, left empty while e-invoicing does not apply. No integration with the Invoice Registration Portal SHALL be performed.

#### Scenario: Fields exist and remain empty

- **WHEN** an invoice is issued
- **THEN** the IRN, signed QR payload, and acknowledgement fields are present on the record and empty
- **AND** no request is made to any external invoicing portal
