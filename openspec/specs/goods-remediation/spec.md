# goods-remediation

## Purpose

Liquidation goods arrive imperfect but often fixable — an induction that works but is filthy, a
kettle sound but missing its base, a shirt perfect but creased. This capability holds such goods in
a dedicated **needs-work** state, records the kind of work each needs (scoped to its department),
and moves units between states as they are prepared into sellable stock, rescued, or found wanting —
all without rewriting the append-only stock ledger.

The states named here map to the shop's language and the existing stock model: **Ready** is
`GOOD`, **Seconds** is `DAMAGED`, **Scrap** is `UNUSABLE`, and **Needs-work** is the new state this
capability introduces. "Stock-bearing" means a state whose units are on hand and reach the ledger —
`GOOD` and `DAMAGED`. `UNUSABLE` and `NEEDS_WORK` stay off the ledger; needs-work is owned and
costed but not on hand until it is made ready.

## Requirements

### Requirement: A needs-work state for goods that must be prepared before sale

The system SHALL record stock that has arrived and is functional or fixable but not yet
shelf-ready in a distinct **needs-work** state, separate from Ready (`GOOD`), Seconds (`DAMAGED`),
and Scrap (`UNUSABLE`). Needs-work goods SHALL carry the ordinary cost of their lot, because they
will sell at full price once prepared — they are not worth less, only not yet ready.

#### Scenario: An item that works but is not shelf-ready is not marked damaged

- **WHEN** an operator counts an item that works but is dirty, creased, or incomplete
- **THEN** it is recorded as needs-work, and is neither sold as seconds nor placed on the shelf

#### Scenario: Needs-work carries ordinary cost at lot close

- **WHEN** a lot is closed and its cost apportioned
- **THEN** needs-work units are counted in the cost divisor exactly as Ready units, since both will sell at full price

### Requirement: Needs-work goods are not sellable until prepared

The system SHALL exclude needs-work goods from sale. A product SHALL NOT become sellable on the
strength of needs-work stock alone; only stock in a sellable state — Ready, or Seconds at its own
price — counts toward sellability.

#### Scenario: A product whose only stock is needs-work cannot be sold

- **WHEN** every unit of a product is in the needs-work state
- **THEN** the product is not sellable, however completely it is priced and labelled

#### Scenario: Preparing the goods makes them sellable

- **WHEN** needs-work stock is moved to Ready
- **THEN** it becomes sellable under the ordinary gate (priced, MRP present, labelled)

### Requirement: Each needs-work unit records the kind of work it needs

The system SHALL attach an **issue type** to needs-work goods, naming the work required — for
example cleaning, repair, rebuild, or repackaging. A product whose units need different kinds of
work SHALL be able to hold them under their own issue types at the same time.

#### Scenario: Counting into needs-work records an issue type

- **WHEN** goods are counted as needs-work
- **THEN** an issue type is recorded against them

#### Scenario: One product, two kinds of work

- **WHEN** some units of a product need cleaning and others need repair
- **THEN** each set is held under its own issue type and counted apart

### Requirement: Issue types are drawn from a menu scoped to the product's category

The system SHALL offer only the issue types applicable to a product's category, and issue types
SHALL be data-driven rows keyed to category rather than a fixed enum, so the menu differs by
category and can change without a code release — the same choice already made for `Category`.

#### Scenario: A clothing item offers dry-cleaning, not rebuild

- **WHEN** an operator marks a FASHION item as needs-work
- **THEN** dry-cleaning is offered and rebuild is not

#### Scenario: An appliance offers rebuild and repair, not dry-cleaning

- **WHEN** an operator marks a KITCHEN item as needs-work
- **THEN** rebuild and repair are offered and dry-cleaning is not

#### Scenario: A category's menu can change without code

- **WHEN** the issue types for a category are changed in the data
- **THEN** the new menu is offered without a code change

### Requirement: Units can be moved between states as their condition changes

The system SHALL provide an operation to move a quantity of a product's units from one state to
another: needs-work to Ready when prepared; Seconds or Scrap to Ready when a unit is rescued
(cleaned, or rebuilt from another unit's parts); and Ready to Seconds or Scrap when damage is found
after counting. The operation SHALL refuse to move more units than the source state holds.

#### Scenario: A prepared unit becomes Ready

- **WHEN** a needs-work unit's work is done and it is moved to Ready
- **THEN** the needs-work pile falls by one, the Ready pile rises by one, and it becomes sellable

#### Scenario: A rescued unit is returned to stock

- **WHEN** a unit is rebuilt from another's parts and moved from Scrap or Seconds to Ready
- **THEN** it is held as Ready stock

#### Scenario: Damage found later is recorded

- **WHEN** a Ready unit is found damaged and moved to Seconds or Scrap
- **THEN** it leaves the Ready pile for the new state

#### Scenario: A move larger than the source is refused

- **WHEN** a move asks for more units than the source state holds
- **THEN** the move is refused and nothing changes

### Requirement: State changes are recorded without rewriting the ledger

The system SHALL record every state change as append-only ledger movements on the stock-bearing
states, never as an edit or deletion. Scrap SHALL remain off the ledger, so on-hand rises when
scrap is rescued into a stock-bearing state and falls when stock-bearing goods are scrapped, while
units moving between two stock-bearing states leave on-hand unchanged.

#### Scenario: Moving between two stock-bearing states holds on-hand steady

- **WHEN** a unit moves from Seconds to Ready
- **THEN** on-hand is unchanged and both piles reflect the move

#### Scenario: Rescuing scrap raises on-hand

- **WHEN** a Scrap unit is rescued to Ready
- **THEN** on-hand rises by one

#### Scenario: Scrapping stock lowers on-hand

- **WHEN** a Ready unit is scrapped
- **THEN** on-hand falls by one

#### Scenario: Nothing already written is disturbed

- **WHEN** any state change is recorded
- **THEN** no existing ledger entry is altered or deleted

### Requirement: A product's MRP is shared with its needs-work and rescued units

Since needs-work goods sell at full price once prepared, the system SHALL treat their MRP as it
treats any other unit of the product — captured once per product per delivery and shared across all
of that product's states, so a unit does not arrive at the shelf unpriced merely because it passed
through needs-work.

#### Scenario: A rescued unit carries the product's MRP

- **WHEN** a product's MRP is known and a needs-work unit is moved to Ready
- **THEN** the Ready unit carries that MRP without being asked again

### Requirement: State changes apply while a lot's cost is still open

The system SHALL permit state changes for goods whose lot has not been closed. Reclassifying goods
after a lot is closed would disturb costs already settled and prices possibly already set, and is
out of scope for this change; such a change SHALL be refused rather than silently corrupt settled
figures.

#### Scenario: An open lot accepts a state change

- **WHEN** a lot is open
- **THEN** its units may be moved between states

#### Scenario: A closed lot refuses a state change

- **WHEN** a lot is closed
- **THEN** a state change against its goods is refused
