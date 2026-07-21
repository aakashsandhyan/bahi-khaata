## MODIFIED Requirements

### Requirement: Lot cost is allocated across its products by relative retail value

The cost of a lot SHALL be allocated across the products it contains in proportion to a stated value supplied per line, so that products of different worth carry proportionally different costs. That value SHALL be an input to allocation rather than read from any particular field, because a manifest may state a marketplace selling price, a supplier's cost, or nothing at all.

Where a line's stated value is unknown, an estimated value SHALL be supplied and used in its place.

A line's stated value SHALL be expressed per unit, and quantity SHALL be applied by the allocation itself. Supplying a line total instead applies quantity twice, which inflates the share of multi-unit lines and squeezes single-unit lines while leaving the lot total correct.

Allocation by unit count SHALL NOT be used for mixed lots, because it assigns the same cost to items of wildly different value and renders per-product margin meaningless.

#### Scenario: Cost is allocated in proportion to stated value

- **WHEN** a lot containing several products is allocated
- **THEN** each line receives a share of the lot amount in proportion to its quantity multiplied by its per-unit stated value

#### Scenario: An unknown value uses an estimate

- **WHEN** a line has no stated value
- **THEN** an estimated value is required for that line
- **AND** allocation uses that estimate

#### Scenario: Per-unit cost derives from the line allocation

- **WHEN** a line has been allocated its share of the lot amount
- **THEN** the batch's per-unit cost is that share divided by the sellable quantity received

#### Scenario: Quantity is applied once

- **WHEN** two lines carry equal per-unit stated values and differing quantities
- **THEN** each line's per-unit cost is equal
- **AND** the line with the greater quantity receives proportionally more of the lot amount in total

#### Scenario: A uniform proportion of stated value reaches every line

- **WHEN** the amount paid for a lot is a fixed fraction of the total stated value of its lines
- **THEN** every line's allocated cost is that same fraction of its own stated value

#### Scenario: A correct total does not imply a correct split

- **WHEN** allocated shares sum exactly to the amount paid
- **THEN** that alone does not establish that the shares are correctly proportioned
- **AND** correctness of the split is established by comparing lines against one another

## ADDED Requirements

### Requirement: Allocation runs against received quantities, at a defined point

Allocation SHALL take the quantities actually received as its input, and SHALL run at the point a lot is closed rather than when its manifest is imported. Before that point a batch SHALL carry stock with no allocated cost, and that state SHALL be distinguishable from a cost of zero.

#### Scenario: An uncosted batch is distinguishable from a free one

- **WHEN** a batch belongs to a lot that has not been closed
- **THEN** its allocated cost is reported as not yet determined rather than as zero

#### Scenario: Valuation excludes uncosted stock rather than valuing it at zero

- **WHEN** stock is valued while some of it belongs to an open lot
- **THEN** the uncosted stock is reported separately rather than counted at zero

#### Scenario: Expected but unreceived quantities take no share

- **WHEN** a lot is closed and some expected lines were never received
- **THEN** those lines receive no share of the amount paid
