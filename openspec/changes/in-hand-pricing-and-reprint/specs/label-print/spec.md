## ADDED Requirements

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
