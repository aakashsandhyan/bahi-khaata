## ADDED Requirements

### Requirement: Batch-level bin is a nullable free-text tag
Each batch SHALL carry an optional bin — a nullable, free-text tag naming where that stock physically sits. There SHALL be no bin registry or lookup table and no normalisation: a bin is an operator's own shorthand stored verbatim on the batch, and an unset bin SHALL be NULL.

#### Scenario: A bin is stored verbatim
- **WHEN** a batch is given a bin value
- **THEN** the value is stored as free text on the batch with no registry lookup or normalisation

#### Scenario: An unset bin is NULL
- **WHEN** a batch has never been assigned a bin
- **THEN** its bin is NULL

### Requirement: Bin is assigned at pricing and editable at item detail
A batch's bin SHALL be settable from two places: the pricing workbench MAY set it on its existing save (no new round trip), and the item detail per-batch list SHALL edit it through `PUT /api/inventory/batch/{id}/bin`. A blank or whitespace-only bin value SHALL be trimmed to NULL rather than stored as an empty string.

#### Scenario: Pricing save sets the bin
- **WHEN** the operator supplies a bin while saving a product in the pricing workbench
- **THEN** the batch is saved with that bin on the same save, without an extra round trip

#### Scenario: Item detail edits the bin
- **WHEN** the operator edits a batch's bin through the bin endpoint
- **THEN** the batch's bin is updated to the new value

#### Scenario: Blank bin trims to NULL
- **WHEN** a bin is submitted as blank or whitespace only
- **THEN** it is stored as NULL, not an empty string

### Requirement: Bin is shown and filterable in inventory
The Inventory table SHALL show each row's bin(s) and let the operator filter rows by bin. A row with no bin SHALL render its bin cell as an em dash rather than an empty string.

#### Scenario: Bin appears in the inventory row
- **WHEN** a row's stock has an assigned bin
- **THEN** the inventory row shows that bin and the row can be matched by the bin filter

#### Scenario: Absent bin renders as an em dash
- **WHEN** a row's stock has no bin
- **THEN** its bin cell renders as an em dash, not an empty string
