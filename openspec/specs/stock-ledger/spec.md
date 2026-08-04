# stock-ledger Specification

## Purpose
TBD - created by archiving change goods-in-from-manifest. Update Purpose after archive.
## Requirements
### Requirement: A product's cost is its stated value scaled by its lot's rate, pinned at receipt

The cost of each product SHALL be its manifest-stated value scaled by the rate its lot was bought at — the amount paid over the total stated value of the lot's expected goods — and SHALL be pinned onto its batch as the stock is received (`CostBasis.PINNED`). A manifest states a value, not a cost outright: a returns line's online selling price, a supply line's supplier cost. Each category is bought as its own lot, so the rate is that category's single figure — below one where a returns lot was discounted, above one where a supply lot carried a markup.

The rate SHALL be computed over expected quantities, so goods that fail to arrive do not raise the cost of the units that did. Cost SHALL NOT be a share of a lot total redistributed across differently-valued goods; each product's cost stands on its own stated value scaled by the one rate, and a shortfall or a damaged unit SHALL NOT shift cost onto the survivors.

A stated value SHALL be expressed per unit; a line total SHALL NOT be used, because applying quantity twice would inflate multi-unit lines and squeeze single-unit ones.

Where a manifest line states no value — a genuine surplus that appears on no line — the batch SHALL remain uncosted, an explicit state distinct from a cost of zero, rather than take an invented share of anything.

#### Scenario: Cost is the stated value scaled by the lot rate

- **WHEN** a product is received against a manifest line stating a per-unit value, its lot bought at a known fraction of its total stated value
- **THEN** its batch's per-unit cost is that stated value times the fraction, pinned to the batch

#### Scenario: One rate per category, no redistribution across the lot

- **WHEN** the products of a lot are received
- **THEN** each carries its own stated value scaled by the lot's single rate
- **AND** no lot total is redistributed among them, and a shortfall shifts no cost onto the units that arrived

#### Scenario: A surplus with no stated value stays uncosted

- **WHEN** goods arrive that no manifest line names, so no value is stated for them
- **THEN** their batch is uncosted — reported as not yet determined, never as zero

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

### Requirement: Selling decrements stock through the ledger, and may go negative

When a sale is completed, the system SHALL write `SALE` ledger movements that decrement the sold products' stock, drawn FIFO across each product's batches and capturing the cost of goods sold at each batch's cost. Selling SHALL NOT be blocked by the counted on-hand — the counter is the source of truth — so on-hand MAY go negative, and that negative stands as the true record that more was sold than the count showed.

#### Scenario: A sale decrements stock FIFO
- **WHEN** a sale of several units of a product is completed and the product has stock in more than one batch
- **THEN** `SALE` movements are written consuming the oldest batch first, then the next, each carrying that batch's cost as the cost of goods sold
- **AND** the product's on-hand drops by the quantity sold

#### Scenario: Selling more than the count shows drives on-hand negative
- **WHEN** a sale's quantity exceeds the product's counted on-hand
- **THEN** the sale still completes, the extra units are recorded against the newest batch, and that batch's on-hand goes negative
- **AND** the negative on-hand remains as the record that a recount is needed

