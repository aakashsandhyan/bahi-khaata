# label-print Specification

## Purpose
TBD - created by archiving change pricing-and-label-print. Update Purpose after archive.
## Requirements
### Requirement: The print request is self-contained
A print job SHALL carry the label fields it needs — barcode, product name, selling price, and
optional MRP — plus a copy count. The executor SHALL render the label from those fields and
SHALL NOT read the database to render. A job MAY carry a product reference used only to mark
that product's label as printed on success, never to render.

#### Scenario: Rendering from the job's own fields
- **WHEN** the executor processes a queued job
- **THEN** it renders the label from the job's barcode, name, price, and optional MRP without a
  database lookup

#### Scenario: A later price change does not alter a queued label
- **WHEN** a product's selling price changes after a job for it was queued
- **THEN** the queued job still prints the price captured when it was queued

### Requirement: MRP prints only when it is a real saving
The label SHALL show the MRP struck through with the saving percentage only when an MRP is
present and above the selling price. When no MRP is present, or it is not above the selling
price, the label SHALL show the selling price alone with no strike and no saving claimed.

#### Scenario: MRP above the price
- **WHEN** a job has an MRP greater than its selling price
- **THEN** the label shows the struck MRP and the derived saving percentage

#### Scenario: No usable MRP
- **WHEN** a job has no MRP, or an MRP at or below its selling price
- **THEN** the label shows only the selling price

### Requirement: A printed label marks its product
On a successful print of a job that references a product, the system SHALL mark that product's
label as printed, so it no longer appears among products awaiting a label.

#### Scenario: Marking on success
- **WHEN** a job referencing a product prints successfully
- **THEN** that product is marked label-printed and drops out of the bulk-print list

#### Scenario: A failed print leaves the product unmarked
- **WHEN** a job fails after its retries
- **THEN** the referenced product is not marked printed and remains in the bulk-print list

### Requirement: Bulk printing labels for shelf products
The system SHALL list shelf products that have no label printed yet and SHALL queue a
self-contained job for each selected product. Within one bulk run the labels SHALL be paired
onto the two-up rows so a run of N products prints ceil(N/2) rows, and a lone leftover SHALL
print as a duplicate pair rather than a blank sticker.

#### Scenario: Bulk queue of unlabelled products
- **WHEN** the user selects several unlabelled shelf products and starts a bulk print
- **THEN** a self-contained job is queued for each, and the labels are paired onto rows

#### Scenario: Odd count leaves no blank
- **WHEN** a bulk run has an odd number of products
- **THEN** the leftover product prints as a duplicate pair and no sticker is left blank

### Requirement: Reprint a label by barcode

A priced product's label SHALL be reprintable from its **barcode alone**, without re-pricing it and without touching stock. Given a barcode, the system SHALL resolve the product's **current** name, selling price, and MRP, and queue the asked-for number of labels through the existing self-contained print path. This SHALL be reachable on its own screen, and is the one place a product can be looked up by barcode.

An **unknown** barcode, or one whose product has **no selling price yet**, SHALL be refused with a clear message rather than queued.

#### Scenario: Reprint an existing label

- **WHEN** the `BBZ-…` barcode of a priced product is entered with a quantity of 3
- **THEN** 3 labels carrying its current name, price, and MRP are queued, and no stock moves

#### Scenario: The label reflects a corrected figure

- **WHEN** a product's MRP was corrected after its first label, and its barcode is reprinted
- **THEN** the reprinted label carries the corrected MRP, not the one on the old sticker

#### Scenario: Unknown barcode is refused

- **WHEN** a barcode matching no product is entered
- **THEN** the reprint is refused with a message that nothing matches it

#### Scenario: Unpriced product is refused

- **WHEN** the barcode of a product with no selling price is entered
- **THEN** the reprint is refused with a message that it must be priced first

