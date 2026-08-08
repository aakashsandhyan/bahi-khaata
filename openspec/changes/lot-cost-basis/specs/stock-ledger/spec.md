## MODIFIED Requirements

### Requirement: A product's cost is its stated value scaled by its lot's rate, pinned at receipt

Where a lot declares no cost basis, the cost of each product SHALL be its manifest-stated value scaled by the rate its lot was bought at — the amount paid over the total stated value of the lot's expected goods — and SHALL be pinned onto its batch as the stock is received (`CostBasis.PINNED`). A manifest states a value, not a cost outright: a returns line's online selling price, a supply line's supplier cost. Each category is bought as its own lot, so the rate is that category's single figure — below one where a returns lot was discounted, above one where a supply lot carried a markup.

Where a lot **declares a cost basis**, the cost of each product SHALL instead be derived from that basis — a flat per-unit cost, a percentage of an MRP or ASP anchor, a cost banded by MRP, or a multiplier on a chosen base — and SHALL be pinned onto its batch as an ordinary pinned cost (`CostBasis.PINNED`); the amount paid then serves only as a cross-check, not as the rate. The two are exclusive per lot: a declared basis governs, otherwise the rate formula stands.

The rate SHALL be computed over expected quantities, so goods that fail to arrive do not raise the cost of the units that did. Cost SHALL NOT be a share of a lot total redistributed across differently-valued goods; each product's cost stands on its own stated value scaled by the one rate, and a shortfall or a damaged unit SHALL NOT shift cost onto the survivors.

A stated value SHALL be expressed per unit; a line total SHALL NOT be used, because applying quantity twice would inflate multi-unit lines and squeeze single-unit ones.

Where a manifest line states no value — a genuine surplus that appears on no line — and the lot declares no basis to derive one, the batch SHALL remain uncosted, an explicit state distinct from a cost of zero, rather than take an invented share of anything.

#### Scenario: Cost is the stated value scaled by the lot rate

- **WHEN** a product is received against a manifest line stating a per-unit value, its lot bought at a known fraction of its total stated value, and the lot declares no cost basis
- **THEN** its batch's per-unit cost is that stated value times the fraction, pinned to the batch

#### Scenario: A declared cost basis governs the cost instead of the rate

- **WHEN** a product is received into a lot that declares a cost basis
- **THEN** its per-unit cost is derived from that basis and pinned to the batch, and the lot rate is not used

#### Scenario: One rate per category, no redistribution across the lot

- **WHEN** the products of a lot with no declared basis are received
- **THEN** each carries its own stated value scaled by the lot's single rate
- **AND** no lot total is redistributed among them, and a shortfall shifts no cost onto the units that arrived

#### Scenario: A surplus with no stated value stays uncosted

- **WHEN** goods arrive that no manifest line names, so no value is stated for them, and the lot declares no basis to derive a cost
- **THEN** their batch is uncosted — reported as not yet determined, never as zero

### Requirement: A batch is costed at receipt, not at lot close

A batch SHALL be costed the moment the inputs its cost needs are known, and SHALL NOT wait for the lot to be closed. For a rate-costed or flat-costed batch that is the moment of receipt; for a batch whose lot anchors its cost to an MRP or ASP that is the moment the MRP is recorded or the online price is observed. A product with a costed batch MAY be priced as soon as it is costed. Only a batch whose cost cannot yet be determined — a surplus with no stated cost, or an anchor not yet known — carries no cost, and that state SHALL be distinguishable from a cost of zero.

#### Scenario: Costed at receipt, priceable before close

- **WHEN** stock is received against a manifest line with a stated cost, or into a lot with a flat cost basis
- **THEN** its batch is costed immediately
- **AND** the product can be priced without the lot being closed

#### Scenario: An anchored cost is determined when its anchor is known

- **WHEN** a batch's lot anchors its cost to MRP and the MRP has not yet been recorded
- **THEN** the batch is uncosted until the MRP is recorded, at which point it is costed

#### Scenario: An uncosted batch is distinguishable from a free one

- **WHEN** a batch has no pinned cost because its goods were a surplus or its anchor is not yet known
- **THEN** its cost is reported as not yet determined rather than as zero
