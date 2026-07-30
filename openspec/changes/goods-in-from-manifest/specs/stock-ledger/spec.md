## MODIFIED Requirements

### Requirement: A product's cost is the manifest's stated per-unit cost, pinned at receipt

The cost of each product SHALL be the per-unit cost its manifest line states, pinned onto its batch as the stock is received (`CostBasis.PINNED`). Cost SHALL NOT be apportioned from a lot total across the products it contains — the manifest states each product's cost directly, so there is nothing to divide.

A stated cost SHALL be expressed per unit; a line total SHALL NOT be used, because applying quantity twice would inflate multi-unit lines and squeeze single-unit ones.

Where a manifest line states no cost — a genuine surplus that appears on no line — the batch SHALL remain uncosted, an explicit state distinct from a cost of zero, rather than take an invented share of anything.

#### Scenario: Cost is the stated per-unit figure

- **WHEN** a product is received against a manifest line stating a per-unit cost
- **THEN** its batch's per-unit cost is that stated figure, pinned to the batch

#### Scenario: No apportionment across the lot

- **WHEN** the products of a lot are received
- **THEN** each carries its own stated per-unit cost
- **AND** no lot total is divided among them

#### Scenario: A surplus with no stated cost stays uncosted

- **WHEN** goods arrive that no manifest line names, so no cost is stated for them
- **THEN** their batch is uncosted — reported as not yet determined, never as zero

## ADDED Requirements

### Requirement: A batch is costed at receipt, not at lot close

A batch SHALL be costed the moment its stock is received, from its pinned cost, and SHALL NOT wait for the lot to be closed. A product with a costed batch MAY therefore be priced as soon as it is received. Only an uncosted batch — a surplus with no stated cost — carries no cost, and that state SHALL be distinguishable from a cost of zero.

#### Scenario: Costed at receipt, priceable before close

- **WHEN** stock is received against a manifest line with a stated cost
- **THEN** its batch is costed immediately
- **AND** the product can be priced without the lot being closed

#### Scenario: An uncosted surplus is distinguishable from a free one

- **WHEN** a batch has no pinned cost because its goods were a surplus
- **THEN** its cost is reported as not yet determined rather than as zero

### Requirement: The amount paid is reconciled against the pinned line costs

A lot's amount paid SHALL be kept as a recorded fact and reconciled against the sum of its received lines' pinned per-unit costs times their received quantities. A material difference SHALL be reported — for reporting and audit — rather than apportioned into the goods. The amount paid SHALL NOT be the source of any product's cost.

#### Scenario: The pinned costs reconcile to the amount paid

- **WHEN** a lot's received line costs sum to its amount paid
- **THEN** the lot reconciles cleanly and no discrepancy is raised

#### Scenario: A mismatch is flagged, not absorbed

- **WHEN** the sum of a lot's received pinned costs differs materially from its amount paid
- **THEN** the difference is reported as a discrepancy
- **AND** no product's cost is silently adjusted to make the totals agree
