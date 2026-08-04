# receipt-print Specification

## Purpose
TBD - created by archiving change bill-printing. Update Purpose after archive.
## Requirements
### Requirement: A completed sale prints a bill on the receipt printer

The system SHALL print a bill for a completed sale on a dedicated ESC/POS thermal receipt printer, separate from the label printer. The bill SHALL show the shop header, bill number and date/time, one line per item (name, quantity, unit price, line total), the total saved against MRP, the grand total, and the payment method.

#### Scenario: The bill prints on completion
- **WHEN** a sale is completed and the receipt printer is available
- **THEN** a bill for that sale is printed, showing its items, total saving, grand total, bill number, and payment method

### Requirement: The bill is rendered from editable settings

The system SHALL render the bill's shop name, address, GSTIN, bill title, declaration, and footer from admin-editable settings, so the tax treatment can be changed without code. When configured as a composition Bill of Supply, the bill SHALL carry the GSTIN and the composition declaration and SHALL show no tax lines.

#### Scenario: Composition bill of supply
- **WHEN** the bill settings are configured for the composition scheme
- **THEN** the bill is titled "Bill of Supply", carries the shop's GSTIN and the "Composition taxable person, not eligible to collect tax on supplies" declaration
- **AND** no per-line or summary tax amount is printed

#### Scenario: Settings change without code
- **WHEN** an admin edits the shop name, GSTIN, bill title, or declaration
- **THEN** subsequently printed bills reflect the new settings

### Requirement: A print failure never loses the sale

The system SHALL keep a completed sale recorded even if its bill fails to print. A failed or unconfigured receipt printer MUST NOT roll back the sale; the failure is reported so the bill can be reprinted later.

#### Scenario: Printer offline at completion
- **WHEN** a sale is completed but the receipt printer is offline or not configured
- **THEN** the sale is still recorded with its bill number, and the response reports that printing failed
- **AND** the bill can be reprinted once the printer is available

### Requirement: A bill can be reprinted from the stored sale

The system SHALL reprint a bill by re-rendering it from the persisted sale, producing the same bill as first issued.

#### Scenario: Reprint after a jam
- **WHEN** a user reprints a sale's bill
- **THEN** the reprinted bill matches the original, rendered from the stored sale rather than a live cart

### Requirement: The receipt printer is configured and testable

The system SHALL hold the receipt printer's connection (address and transport) in its own configuration, independent of the label printer, and SHALL let an admin send a test print to verify it.

#### Scenario: Test the receipt printer
- **WHEN** an admin runs a test print against the configured receipt printer
- **THEN** a test slip prints, and the result (success or the error) is reported

