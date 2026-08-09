## MODIFIED Requirements

### Requirement: Completing a sale records it and decrements stock

The system SHALL complete a cart into a persisted sale in a single transaction: it creates an
immutable `Sale` with a snapshot of every line, assigns a bill number, records the payment method,
records the GST breakdown (taxable value, CGST, SGST) extracted inclusively from the line prices,
and writes the stock movements that decrement inventory. The customer bill is a render of this
stored sale.

#### Scenario: A cart is completed
- **WHEN** an operator completes a non-empty cart with a payment method
- **THEN** a `Sale` is persisted with a bill number, the payment method, the subtotal, the total
  saving against MRP, the grand total, and the invoice GST breakdown (taxable value, CGST, SGST)
- **AND** each cart line is stored as an immutable sale line snapshotting its name, barcode, MRP,
  unit price, quantity, line total, saving, and its GST rate and per-line tax
- **AND** stock is decremented for every line (see the stock-ledger capability)

#### Scenario: An empty cart cannot be completed
- **WHEN** an operator completes a cart with no lines
- **THEN** the completion is rejected and no sale is recorded

#### Scenario: An unpriced line blocks completion
- **WHEN** a cart line does not resolve to a priced product
- **THEN** the completion is rejected with a message naming the offending item, and no sale is
  recorded

## ADDED Requirements

### Requirement: The cart total is GST-inclusive, never inflated

The system SHALL treat the selling price as GST-inclusive: the cart and sale total SHALL equal the
sum of the line prices, and GST SHALL be shown as extracted from that total, never added on top. The
prior placeholder that added a flat percentage to the subtotal SHALL be removed.

#### Scenario: The screen total matches the priced goods
- **WHEN** a cart holds items totalling ₹510 of selling price
- **THEN** the cart total shown and rung is ₹510, with GST displayed as the portion extracted from
  it — not ₹510 plus a tax add-on
