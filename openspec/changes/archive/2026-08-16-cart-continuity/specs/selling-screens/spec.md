# selling-screens — Delta Spec

## ADDED Requirements

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
