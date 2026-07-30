## ADDED Requirements

### Requirement: A product records when its label was printed
A product SHALL carry a label-printed marker, unset until a label for it prints successfully and
set at that moment. The marker SHALL let the catalogue and the bulk-print screen distinguish
shelf products that still need a label from those already labelled.

#### Scenario: Unlabelled after pricing without printing
- **WHEN** a product is priced and shelved but its label is not printed
- **THEN** its label-printed marker is unset and it appears among products awaiting a label

#### Scenario: Marked once a label prints
- **WHEN** a label for the product prints successfully
- **THEN** its label-printed marker is set and it no longer appears among products awaiting a
  label

#### Scenario: Reprinting an already-labelled product
- **WHEN** the user reprints a label for a product already marked
- **THEN** the reprint is allowed and the marker stays set
