# selling-screens Specification

## Purpose
The modern selling surfaces — the register-gated POS checkout (quick picks, manual entry) and the Invoices bill record — over the existing sale engine.

## Requirements
### Requirement: The modern checkout sells only through an open register

The modern checkout SHALL show its register's session state and SHALL require an open session before completing a sale; when no session is open it SHALL offer opening one inline (float entry) rather than a dead end. Scanning, cart editing, and payment SHALL use the existing sale engine unchanged — bill number, GST, and receipt exactly as the classic till produces.

#### Scenario: Selling with the register open
- **WHEN** Register 1 is open and a cart is paid from the modern checkout
- **THEN** the sale completes through the existing engine and references the session

#### Scenario: Register closed at sale time
- **WHEN** the modern checkout is used with no open session
- **THEN** completing is withheld and the screen offers to open the register inline

### Requirement: Invoices lists, finds, and reprints bills

The Invoices screen SHALL list recorded sales newest-first with bill number, time, item count, payment method, and total; SHALL find a bill by number; and SHALL reprint any bill from its persisted sale. It SHALL be able to show one session's bills for the close-drawer review.

#### Scenario: Finding and reprinting a bill
- **WHEN** a bill number is searched on Invoices
- **THEN** that sale is shown and can be reprinted as issued

#### Scenario: Reviewing a session's bills at close
- **WHEN** the close-drawer view asks for the open session's sales
- **THEN** Invoices shows exactly the bills recorded against that session

### Requirement: Manual entry sells a thing with no product record

The checkout SHALL offer manual entry: a keyed name and price form a cart line with no product
reference. GST SHALL resolve from a chosen sub-category or the shop default; an optional lot
SHALL attribute the line to a delivery for recovery reporting. No stock ledger movement SHALL be
written for such a line, and an optional MRP below the price SHALL be refused.

#### Scenario: A loose item is keyed and sold
- **WHEN** a name and price are keyed through manual entry and the cart is paid
- **THEN** the sale records the line with no product reference, GST extracted at the chosen rate, and no stock movement

#### Scenario: Lot attribution is optional
- **WHEN** a manual entry names a delivery lot
- **THEN** the sale line carries that lot reference; without one the line records unattributed

### Requirement: Payment leads with the customer ask

Taking payment SHALL present the customer step before the payment method: a mobile field, focused, where ten digits look up the customer — a match shows the name to confirm and attach, an unknown number reveals a name field to save and attach. A **Walk-in** control SHALL always be one tap and SHALL proceed straight to the payment method with no customer. The sale SHALL never be blocked by the customer step — a failed lookup proceeds as walk-in.

#### Scenario: A known customer attaches in one confirm
- **WHEN** a known mobile is keyed at the customer step
- **THEN** the stored name is shown, and confirming attaches the customer and moves to the payment method

#### Scenario: Declining is one tap
- **WHEN** Walk-in is tapped at the customer step
- **THEN** the payment method appears at once and the sale completes unreferenced

#### Scenario: A lookup failure never blocks the sale
- **WHEN** the customer lookup cannot be reached
- **THEN** the flow proceeds as walk-in and the sale completes

### Requirement: The cart names its customer

Once a customer is attached, the cart header SHALL show their name in place of "Walk-in customer"; clearing the cart SHALL clear the attachment.

#### Scenario: The header follows the attachment
- **WHEN** a customer is attached and the cart is then cleared
- **THEN** the header shows the name after attaching and "Walk-in customer" again after clearing

### Requirement: The POS offers Hold and the carts panel

The checkout SHALL offer a Hold control that parks the current cart (leaving it open with its customer) and opens a fresh one, and a carts control showing the count of open carts. Opening it SHALL list the open carts newest touch first — who (or Walk-in), item summary, amount, register, last touch — with the cart on this screen marked. Tapping a row SHALL show an inline preview of its lines with a resume action that loads that cart onto this screen.

#### Scenario: Hold, serve, return
- **WHEN** a cart is held, another customer is rung up, and the held row is resumed from the panel
- **THEN** the held cart's lines and customer return to the screen exactly

#### Scenario: The panel mirrors the floor
- **WHEN** three carts are open across both registers
- **THEN** the panel lists all three newest-first with this screen's cart marked

### Requirement: An invoice opens as a details modal

Tapping a row on Invoices SHALL open the stored bill in a modal: every line with quantity × price and per-line saving, the GST split as invoiced, the payment method, operator, register, the customer (masked mobile) when one was attached, and a Reprint action. The Invoices table SHALL show a customer column (Walk-in when none).

#### Scenario: The bill answers on screen
- **WHEN** a bill's row is tapped
- **THEN** the modal shows its lines, GST split, and customer, and Reprint prints it as issued

#### Scenario: Walk-ins read as walk-ins
- **WHEN** a sale with no customer appears in the table or modal
- **THEN** it is labeled Walk-in, never blank
