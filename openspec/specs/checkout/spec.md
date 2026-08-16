# checkout Specification

## Purpose
TBD - created by archiving change bill-printing. Update Purpose after archive.
## Requirements
### Requirement: Completing a sale records it and decrements stock

The system SHALL complete a cart into a persisted sale in a single transaction: it creates an immutable `Sale` with a snapshot of every line, assigns a bill number, records the payment method, and writes the stock movements that decrement inventory. The customer bill is a render of this stored sale.

#### Scenario: A cart is completed
- **WHEN** an operator completes a non-empty cart with a payment method
- **THEN** a `Sale` is persisted with a bill number, the payment method, the subtotal, the total saving against MRP, and the grand total
- **AND** each cart line is stored as an immutable sale line snapshotting its name, barcode, MRP, unit price, quantity, line total, and saving
- **AND** stock is decremented for every line (see the stock-ledger capability)

#### Scenario: An empty cart cannot be completed
- **WHEN** an operator completes a cart with no lines
- **THEN** the completion is rejected and no sale is recorded

#### Scenario: An unpriced line blocks completion
- **WHEN** a cart line does not resolve to a priced product
- **THEN** the completion is rejected with a message naming the offending item, and no sale is recorded

### Requirement: A completed sale is immutable

The system SHALL never alter a sale after it is completed. A later change to a product's price, name, or MRP MUST NOT change any figure on a past sale or its reprinted bill.

#### Scenario: A price change does not alter a past sale
- **WHEN** a product's selling price is changed after a sale that included it
- **THEN** the earlier sale's line still shows the price, MRP, and totals as they were at completion

### Requirement: Bill numbers are sequential and unique

The system SHALL assign each completed sale the next bill number in a single running sequence, so no two sales share a number and the order of numbers reflects the order of sales.

#### Scenario: Consecutive sales get consecutive numbers
- **WHEN** two sales are completed one after another
- **THEN** the second sale's bill number is exactly one greater than the first's

### Requirement: Completing a sale is idempotent per cart

The system SHALL complete a given cart at most once. A cart that has been completed MUST NOT be completed again.

#### Scenario: Re-completing a cart is rejected
- **WHEN** an operator completes a cart that is already completed
- **THEN** the request is rejected and no second sale is recorded

### Requirement: Past sales can be listed and reprinted

The system SHALL let a user list recent sales — with bill number, date, total, and payment method — and reprint the bill for any sale, rendered from the stored sale.

#### Scenario: Recent sales are listed
- **WHEN** a user opens the sales list
- **THEN** recent sales are shown newest first, each with its bill number, date/time, total, and payment method

#### Scenario: A bill is reprinted from its sale
- **WHEN** a user reprints a past sale by its bill number
- **THEN** the bill is re-rendered from the stored sale, identical to the one first issued

### Requirement: A cart persists until paid, cleared, or the day ends

An open cart SHALL survive screen reloads: a device that remembers its cart SHALL get the same cart back while it remains open. A cart SHALL leave the open state only by completing into a sale, by being cleared, or by the end-of-day sweep.

#### Scenario: A reload changes nothing
- **WHEN** the till reloads mid-cart and re-fetches its remembered cart
- **THEN** the same cart returns with its lines and customer intact

### Requirement: Open carts are listable, newest touch first

The open carts SHALL be listable ordered by last touch descending, each with its customer name (absent for a walk-in), item count, a first-line summary, the total, the register it was opened on, and when it was last touched. Every cart mutation — adding, quantity, removal, clearing, customer attach — SHALL count as a touch.

#### Scenario: The just-abandoned queue-jumper sorts first
- **WHEN** cart A was touched two minutes after cart B
- **THEN** the list returns A before B

### Requirement: Any register resumes any open cart

Fetching an open cart by id SHALL work regardless of which register opened it. A completed cart SHALL refuse completion again regardless of who resumes it.

#### Scenario: The cart follows the customer across counters
- **WHEN** a cart opened on Register 2 is resumed on Register 1
- **THEN** its lines and customer load there, and it completes there normally

#### Scenario: A double payment is impossible
- **WHEN** two registers hold the same cart and one completes the sale
- **THEN** the other's completion attempt is refused

### Requirement: Yesterday's carts sweep at first touch

An open cart last touched before the current business day (IST) SHALL be marked abandoned by the first read that encounters it — the carts list or a device restoring its pointer — and SHALL NOT appear among open carts thereafter.

#### Scenario: The morning sweep
- **WHEN** the first till of the day lists carts and yesterday's forgotten cart is among them
- **THEN** that cart is abandoned and absent from the returned list

#### Scenario: A remembered cart from yesterday starts fresh
- **WHEN** a device restores a pointer to a cart last touched yesterday
- **THEN** that cart is abandoned and the device gets a fresh cart

### Requirement: The cart carries its customer

Attaching a captured customer SHALL write the customer onto the cart, so a held or resumed cart keeps its person; completing the cart SHALL copy the cart's customer onto the sale. A request-level customer on completion SHALL still be honored when the cart carries none.

#### Scenario: A hold keeps the customer
- **WHEN** a cart with a customer attached is held and later resumed on another register
- **THEN** the resumed cart still names that customer, and its sale references them
