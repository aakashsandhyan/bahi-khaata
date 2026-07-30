## ADDED Requirements

### Requirement: Supplier is a first-class entity

The system SHALL store each supplier as a `supplier` record with a UUID primary key and
the fields: `name` (required), `gstin`, `phone`, `address`, `contactPerson`, `notes`
(all optional), and `active` (boolean, default true). Creation and update timestamps
SHALL be recorded.

#### Scenario: Create supplier with name only

- **WHEN** a supplier is created with a non-blank name and no other fields
- **THEN** the supplier is persisted with a generated UUID, `active` true, and null
  optional fields

#### Scenario: Reject blank name

- **WHEN** a supplier is created with a blank or missing name
- **THEN** the request is rejected with a validation error and nothing is persisted

### Requirement: Supplier identity is hybrid and unique

Supplier identity SHALL be enforced on two keys: the normalized name (trimmed, internal
whitespace collapsed to single spaces, compared case-insensitively) MUST be unique across
all suppliers, and `gstin`, when present, MUST be unique across all suppliers. A null
`gstin` SHALL NOT block creation and multiple suppliers MAY have a null `gstin`.

#### Scenario: Duplicate normalized name rejected

- **WHEN** a supplier "ABC Traders" exists and a new supplier "  abc   traders " is created
- **THEN** the request is rejected as a duplicate name and nothing is persisted

#### Scenario: Duplicate GSTIN rejected

- **WHEN** a supplier with a given GSTIN exists and another supplier is created with the
  same GSTIN
- **THEN** the request is rejected as a duplicate GSTIN

#### Scenario: Multiple suppliers without GSTIN allowed

- **WHEN** two suppliers with distinct names and no GSTIN are created
- **THEN** both are persisted

### Requirement: GSTIN is format-validated when present

When `gstin` is provided it SHALL match the 15-character Indian GSTIN format; a blank or
absent `gstin` is accepted.

#### Scenario: Invalid GSTIN rejected

- **WHEN** a supplier is created with a `gstin` that does not match the 15-character GSTIN
  format
- **THEN** the request is rejected with a validation error

#### Scenario: Absent GSTIN accepted

- **WHEN** a supplier is created with no `gstin`
- **THEN** the supplier is persisted

### Requirement: Suppliers are soft-deleted, never removed

Because lots reference suppliers, a supplier SHALL NOT be hard-deleted. Deactivation
SHALL set `active` to false; a deactivated supplier SHALL remain readable and remain
linked to its existing lots, and MAY be reactivated.

#### Scenario: Deactivate supplier

- **WHEN** an active supplier is deactivated
- **THEN** its `active` flag becomes false and its existing lots remain linked to it

#### Scenario: Reactivate supplier

- **WHEN** a deactivated supplier is reactivated
- **THEN** its `active` flag becomes true

### Requirement: Every lot references a supplier

Each `lot` SHALL carry a `supplier_id` foreign key to a `supplier` record, in addition to
the retained legacy `supplier` text column. The `supplier_id` SHALL be non-null for every
lot.

#### Scenario: Lot links to a supplier

- **WHEN** a lot is created referencing an existing active supplier
- **THEN** the lot's `supplier_id` points at that supplier and the legacy text column
  reflects that supplier's name

### Requirement: Existing lot suppliers are backfilled

A migration SHALL create one supplier per distinct normalized value of the existing
`lot.supplier` text column and set each lot's `supplier_id` to the matching new supplier.
Backfilled suppliers SHALL have a null `gstin` and `active` true. The normalized-name
uniqueness rule SHALL determine which distinct strings collapse into one supplier.

#### Scenario: Distinct strings backfill and link

- **WHEN** lots exist with supplier strings "ABC", "abc ", and "XYZ Traders"
- **THEN** two suppliers are created ("ABC" from the first two, "XYZ Traders" from the
  third) and every lot's `supplier_id` points at the correct one

#### Scenario: All lots linked after backfill

- **WHEN** the backfill migration completes
- **THEN** no lot has a null `supplier_id`

### Requirement: Receipt requires an existing active supplier

At receipt time (manual lot creation and manifest import) the request SHALL identify the
supplier by `supplierId` referencing an existing supplier. A request whose `supplierId`
is missing, unknown, or refers to an inactive supplier SHALL be rejected. New suppliers
SHALL NOT be created implicitly at receipt time.

#### Scenario: Receipt with valid supplier id succeeds

- **WHEN** a lot is received with a `supplierId` of an existing active supplier
- **THEN** the lot is created and linked to that supplier

#### Scenario: Receipt with unknown supplier id rejected

- **WHEN** a lot is received with a `supplierId` that matches no supplier
- **THEN** the request is rejected and no lot is created

#### Scenario: Receipt with inactive supplier rejected

- **WHEN** a lot is received with a `supplierId` of a deactivated supplier
- **THEN** the request is rejected and no lot is created

### Requirement: Suppliers are managed and viewed on the dashboard

The dashboard SHALL provide the ability to list and search suppliers, create a supplier,
edit a supplier, deactivate/reactivate a supplier, and view the lots received from a given
supplier.

#### Scenario: View lots for a supplier

- **WHEN** the lots for a supplier are requested
- **THEN** the system returns the lots whose `supplier_id` matches that supplier

#### Scenario: List filters by active

- **WHEN** the supplier list is requested filtered to active suppliers
- **THEN** only suppliers with `active` true are returned
