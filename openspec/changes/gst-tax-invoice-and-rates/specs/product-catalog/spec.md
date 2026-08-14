## ADDED Requirements

### Requirement: A product carries a GST sub-category

The system SHALL let a product carry an optional `sub_category` drawn from the controlled
sub-category vocabulary (whose parent is the product's category). The sub-category determines the
product's GST rate. It MAY be assigned by the classification backfill, at the till, or by an admin
in the catalogue, and it is editable.

#### Scenario: A product shows and edits its sub-category
- **WHEN** a product is opened in the catalogue
- **THEN** its sub-category (if any) is shown and can be changed to another sub-category under its
  category

#### Scenario: An unclassified product is allowed until sold
- **WHEN** a product has no sub-category yet
- **THEN** it remains a valid catalogue product; the sub-category is required only to ring it at the
  till (see the gst-taxation capability)
