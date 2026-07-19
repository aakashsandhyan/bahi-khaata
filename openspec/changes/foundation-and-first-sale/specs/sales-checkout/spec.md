## ADDED Requirements

### Requirement: Scanning a code adds its product to the sale

The terminal SHALL accept a scanned barcode, resolve it to a product, and add that product to the current sale at the product's selling price. An unrecognised code SHALL be reported to the cashier without interrupting the sale in progress.

#### Scenario: A known code adds a line

- **WHEN** a barcode assigned to a product is scanned into an open sale
- **THEN** a line for that product is added at the product's current selling price

#### Scenario: The same code again increases quantity

- **WHEN** a barcode already present on a line is scanned again
- **THEN** the quantity of that line increases by one rather than a duplicate line being added

#### Scenario: An unrecognised code does not disrupt the sale

- **WHEN** a barcode not assigned to any product is scanned
- **THEN** the terminal reports the code as unrecognised
- **AND** the lines already on the sale are unchanged

#### Scenario: The scanned price comes from the system

- **WHEN** a product is added to a sale
- **THEN** the price used is the product's current selling price as held by the system, irrespective of any price printed on the physical item

#### Scenario: An unpriced product is refused rather than sold at zero

- **WHEN** a barcode resolving to a product with no selling price is scanned
- **THEN** the terminal reports the product as unpriced
- **AND** no line is added to the sale
- **AND** the lines already on the sale are unchanged

### Requirement: A sale accumulates lines and totals before it is finalised

An open sale SHALL be held as a cart recording its lines, quantities, and running total, and SHALL be modifiable until it is finalised. An open cart SHALL have no effect on stock or invoices, and SHALL NOT reserve or withhold stock.

#### Scenario: Running total reflects the lines

- **WHEN** lines are added to an open cart
- **THEN** the cart's total equals the sum of its line values

#### Scenario: An open cart leaves stock untouched

- **WHEN** a cart is open and has not been finalised
- **THEN** no ledger row has been appended for it
- **AND** no invoice exists for it
- **AND** quantity on hand for its products is unchanged

#### Scenario: A line can be removed before finalisation

- **WHEN** a line is removed from an open cart
- **THEN** the line no longer appears on the cart
- **AND** the cart's total is reduced accordingly

### Requirement: An open cart survives interruption of the terminal

The cart SHALL be persisted by the backend rather than held only in the terminal's memory, so that a terminal crash, restart, or power interruption does not lose a sale in progress. On restart the terminal SHALL recover the open cart rather than requiring the basket to be rescanned.

#### Scenario: Scanned lines are recorded as they are added

- **WHEN** a line is added to an open cart
- **THEN** the backend records the line before the terminal reports it as added

#### Scenario: A cart survives a terminal restart

- **WHEN** the terminal is restarted while a cart is open
- **THEN** the terminal recovers that cart with its lines and quantities intact

#### Scenario: A cart survives a power interruption

- **WHEN** power is interrupted after lines have been added and the terminal subsequently restarts
- **THEN** the lines recorded before the interruption are present on the recovered cart

### Requirement: A cart is working state and does not outlive its sale

A cart SHALL be discarded once its sale is finalised, leaving the invoice and its ledger rows as the record. A cart SHALL also be discardable by explicit void, and SHALL expire automatically after a configurable period of inactivity so that forgotten carts do not accumulate. Discarding a cart by any route SHALL leave no invoice and consume no stock.

#### Scenario: Finalisation discards the cart

- **WHEN** a sale is finalised successfully
- **THEN** the cart is discarded
- **AND** the invoice and its ledger rows remain as the record of the sale

#### Scenario: A cart can be voided explicitly

- **WHEN** an open cart is voided
- **THEN** the cart is discarded
- **AND** no invoice is created and no stock is consumed

#### Scenario: A forgotten cart expires

- **WHEN** a cart has been inactive for longer than the configured expiry period
- **THEN** the cart is discarded
- **AND** no invoice is created and no stock is consumed

#### Scenario: Expiry does not affect a cart in use

- **WHEN** a cart has been modified within the configured expiry period
- **THEN** the cart remains open

### Requirement: Finalising a sale applies all of its effects atomically

Finalising a sale SHALL, in a single transaction, issue an invoice, append the corresponding ledger rows, and consume stock FIFO from the oldest available batches. If any part fails, the entire finalisation SHALL be rolled back, leaving no invoice, no ledger row, and no stock consumed.

#### Scenario: A successful finalisation applies every effect

- **WHEN** an open sale is finalised successfully
- **THEN** an invoice is issued for it
- **AND** ledger rows are appended for each line
- **AND** stock is consumed from the oldest available batches

#### Scenario: A failure leaves no partial result

- **WHEN** finalisation fails at any point
- **THEN** no invoice exists for the sale
- **AND** no ledger row has been appended
- **AND** quantity on hand is unchanged

#### Scenario: The invoice reconciles with the ledger

- **WHEN** a sale has been finalised
- **THEN** the quantities on the issued invoice match the quantities recorded in the ledger rows for that sale

### Requirement: A sale cannot consume stock the system does not hold

Finalisation SHALL be refused when a line's quantity exceeds the quantity on hand for that product, and the cashier SHALL be told which product is short. This protects the ledger from recording consumption that never physically occurred.

#### Scenario: Insufficient stock refuses finalisation

- **WHEN** finalisation is attempted for a sale whose line quantity exceeds quantity on hand for that product
- **THEN** finalisation is refused
- **AND** the terminal reports which product is short
- **AND** no invoice is issued and no stock is consumed

#### Scenario: Exactly sufficient stock succeeds

- **WHEN** finalisation is attempted for a sale whose line quantity equals quantity on hand
- **THEN** finalisation succeeds
- **AND** the remaining quantity on hand for that product becomes zero

### Requirement: The terminal does not present checkout until the backend is reachable

Because the terminal holds no database of its own, it SHALL confirm the backend is reachable before presenting the checkout screen, and SHALL display a plain-language waiting state while it is not. It SHALL NOT present a technical error or an unresponsive window to the cashier.

#### Scenario: Waiting state while the backend starts

- **WHEN** the terminal starts before the backend is accepting requests
- **THEN** the terminal displays a plain-language waiting message
- **AND** the checkout screen is not presented

#### Scenario: Checkout appears once the backend answers

- **WHEN** the backend becomes reachable while the terminal is waiting
- **THEN** the terminal presents the checkout screen without requiring a restart

#### Scenario: Loss of the backend is surfaced, not hidden

- **WHEN** the backend becomes unreachable during an open sale
- **THEN** the terminal reports that it cannot reach the backend
- **AND** does not report the sale as finalised
