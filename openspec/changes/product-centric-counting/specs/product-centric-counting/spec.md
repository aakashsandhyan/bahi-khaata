## ADDED Requirements

### Requirement: Counting a product across its boxes in one action

The system SHALL let an operator find a product once and record what was found of it across every
box of an open delivery in a single submission, as a lane parallel to box-by-box counting. The lot
being counted SHALL be chosen first through the catalogue's lot filter, and the product SHALL be
reached through the catalogue — resolved by a scanned code or a name match — so the screen SHALL show
that product's outstanding lines in that lot to count against.

#### Scenario: One find covers many boxes
- **WHEN** a product is selected in the catalogue and it is listed on several boxes of an open lot
- **THEN** every one of those boxes with units still expected is shown together, each with a field to
  enter what was found, so the product is found once rather than once per box

#### Scenario: Only open lots are counted
- **WHEN** a product's lines are requested for a lot that is closed
- **THEN** no lines are offered, because a closed lot is not being counted

### Requirement: A code resolves to exactly one product

When a product is reached by a scanned code that also sits on sibling products (the same item under
several marketplace references), the system SHALL show only the resolved product's lines and SHALL
NOT gather the siblings. Collapsing sibling rows is deferred to pricing.

#### Scenario: Sibling ASINs stay separate
- **WHEN** a code that is mapped to more than one product is used to open the grid
- **THEN** only the single resolved product's lines are shown, and its siblings are counted on their
  own

### Requirement: Per-box entry, one condition and MRP, capped at outstanding

The submission SHALL carry a quantity entered per box, together with a single condition and a single
MRP applied to the whole submission. Each per-box quantity SHALL be capped at that line's current
outstanding, so a product-centric count can never push a line above what the manifest expects. A unit
of a differing condition SHALL still be counted on its own through the box flow.

#### Scenario: A stack of sound stock counted at once
- **WHEN** the operator enters what was found against each box and submits with one condition and one
  MRP
- **THEN** each box's line is counted by that quantity, all recorded with that condition and MRP

#### Scenario: Over-count is refused
- **WHEN** a per-box quantity exceeds that line's outstanding
- **THEN** it is capped at the outstanding, never recorded above expectation

### Requirement: Concurrency is checked at submit, per line

Each per-box entry SHALL carry the outstanding the operator saw when the grid loaded. At submit the
system SHALL re-read each line's current outstanding; where it has changed since — another station
having counted into that box — that entry SHALL be refused and its new outstanding returned for the
operator to re-enter. Entries whose outstanding is unchanged SHALL still be committed.

#### Scenario: Two stations count the same box
- **WHEN** two operators open the same product's grid and both enter a count for the same box, and the
  first submits
- **THEN** the second operator's entry for that box is refused at submit with the box's new
  outstanding, while their other entries commit, so the box is not double-counted

#### Scenario: An untouched line commits normally
- **WHEN** a submitted entry's line has the same outstanding it had when the grid loaded
- **THEN** the entry is counted

### Requirement: Reuses the existing count path and ledger

A product-centric count SHALL be recorded through the same operation as box-by-box counting, so the
batch, the append-only stock ledger, and MRP inheritance are written identically. It SHALL NOT edit
any ledger entry, and SHALL record all accepted entries of one submission in a single transaction.

#### Scenario: Same records as box counting
- **WHEN** a box's line is counted product-centrically
- **THEN** the resulting batch and ledger entries are the same as if that line had been counted box by
  box, and no earlier entry is altered

### Requirement: Only identified goods, and receiving state is untouched

Product-centric counting SHALL be available only for a product the catalogue could resolve — a real
code or a named match. A tagless item, knowable only as the last line in its box, SHALL remain
box-centric. A product-centric count SHALL NOT change any box's received state; marking a box received
remains the box flow's concern, exactly as it is for box-by-box counting.

#### Scenario: Tagless goods stay box-centric
- **WHEN** an item has no identifier to resolve
- **THEN** it cannot be counted product-centrically and is counted in its box by elimination

#### Scenario: Counting does not mark a box received
- **WHEN** a box's line is counted product-centrically
- **THEN** the box's received state is unchanged, just as box-by-box counting leaves it unchanged
