# register-sessions — Delta Spec

## ADDED Requirements

### Requirement: A register opens with a counted float and an operator

Opening a register SHALL create a session carrying the register's name, the operator's name, and the float counted into the drawer, stamped with the opening time. A register with a session already open SHALL refuse to open again until that session closes.

#### Scenario: Opening a register
- **WHEN** an operator opens "Register 1" with a ₹2,000 float
- **THEN** an open session exists for "Register 1" holding the float, the operator, and the opening time

#### Scenario: A register cannot be double-opened
- **WHEN** "Register 1" already has an open session and a second open is attempted
- **THEN** the open is refused and the existing session is untouched

### Requirement: Cash movements record against the open session

Cash put into or taken out of the drawer outside a sale SHALL be recorded against the open session as a movement with direction, amount, and a note. A movement against a register with no open session SHALL be refused.

#### Scenario: Cash taken out for a supplier payment
- **WHEN** ₹500 is taken from Register 1's open drawer with a note
- **THEN** the session carries an OUT movement of ₹500 with that note

### Requirement: Sales attach to the session open at the time of sale

A sale completed while its register has an open session SHALL reference that session. A sale completed with no session — the classic till, or history predating registers — SHALL remain valid with no session reference.

#### Scenario: A modern-checkout sale joins the session
- **WHEN** a sale is completed from a checkout tied to Register 1's open session
- **THEN** the recorded sale references that session

#### Scenario: A classic till sale stays sessionless
- **WHEN** a sale is completed from the classic till with no session
- **THEN** the sale records normally with no session reference

### Requirement: Closing computes and pins over/short

Closing a session SHALL take the counted drawer amount, compute expected cash — float plus the session's cash sales plus cash in minus cash out — and pin the over/short difference on the session with the closing time. A pinned figure SHALL NOT change afterwards.

#### Scenario: A drawer over by 120
- **WHEN** Register 1 closes counted at ₹120 above expected cash
- **THEN** the session records over/short of +₹120 and is closed

#### Scenario: Only cash sales feed expected
- **WHEN** a session held UPI sales and cash sales
- **THEN** expected cash includes only the cash sales
