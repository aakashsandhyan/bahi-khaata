## ADDED Requirements

### Requirement: Pricing reconciles stock through the ledger

Setting the in-hand quantity at pricing SHALL be recorded in the **append-only** stock ledger, never by rewriting a stored counter. On-hand SHALL remain the sum of the ledger's movements.

A **first** pricing SHALL append the difference between the in-hand quantity and current on-hand: a **receipt** when the in-hand is higher, a **correcting negative adjustment** when it is lower. A **later** pricing SHALL append only the added quantity, as a receipt — never a reduction.

A reconciliation that comes to zero (the in-hand equals current on-hand, or nothing is added) SHALL append no movement.

#### Scenario: A higher first count appends a receipt

- **WHEN** a product with on-hand 7 is first priced with an in-hand quantity of 8
- **THEN** a +1 receipt is appended and on-hand is 8

#### Scenario: A lower first count appends a correcting negative

- **WHEN** a product with on-hand 7 is first priced with an in-hand quantity of 5
- **THEN** a −2 correcting adjustment is appended and on-hand is 5

#### Scenario: A later pricing appends only the additions

- **WHEN** an already-priced product with on-hand 4 is priced again with 3 pieces found
- **THEN** a +3 receipt is appended and on-hand is 7

#### Scenario: No change appends nothing

- **WHEN** a product is priced with an in-hand quantity equal to its current on-hand, or re-priced with 0 added
- **THEN** no ledger movement is appended and on-hand is unchanged
