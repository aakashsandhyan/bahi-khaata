## MODIFIED Requirements

### Requirement: The bill is rendered from editable settings

The system SHALL render the bill's shop name, address, GSTIN, bill title, declaration, and footer
from admin-editable settings, so the presentation can be changed without code. For a regular dealer
the bill SHALL be a **Tax Invoice**: it carries the shop's GSTIN and prints the GST breakdown — the
taxable value, CGST, and SGST — alongside the total. The composition "Bill of Supply" defaults
(title and the no-tax declaration) SHALL be retired from the shipped settings.

#### Scenario: Regular-dealer tax invoice
- **WHEN** the bill settings are configured for the regular scheme
- **THEN** the bill is titled "Tax Invoice", carries the shop's GSTIN, and prints the taxable value,
  CGST, and SGST amounts alongside the grand total

#### Scenario: Settings change without code
- **WHEN** an admin edits the shop name, GSTIN, bill title, declaration, or footer
- **THEN** subsequently printed bills reflect the new settings

#### Scenario: The printed tax matches the stored sale
- **WHEN** a bill is printed or reprinted for a completed sale
- **THEN** the CGST, SGST, and taxable value shown are exactly those snapshotted on that sale, not
  recomputed from current rates
