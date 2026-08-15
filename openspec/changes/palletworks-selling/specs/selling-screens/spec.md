# selling-screens — Delta Spec

## ADDED Requirements

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
