## ADDED Requirements

### Requirement: A lot may declare a cost basis

A lot SHALL be able to declare a cost basis — a `strategy`, an `anchor`, and the parameters that strategy needs — that governs how the per-unit cost of every product received into the lot is derived. A lot that declares no cost basis SHALL keep its existing behaviour unchanged. The cost basis SHALL be lot-level: the derivation lives on the lot, and each batch continues to hold only the resulting pinned unit cost (`CostBasis.PINNED`), so nothing new is recorded per batch.

#### Scenario: A lot with no declared basis is unchanged

- **WHEN** a lot is created or received without a cost basis
- **THEN** its products are costed exactly as before (the manifest rate, or the amount-paid apportionment), and no cost-basis behaviour applies

#### Scenario: The basis is recorded on the lot, not the batch

- **WHEN** a lot declares a cost basis and a product is costed from it
- **THEN** the batch holds the derived unit cost as an ordinary pinned cost, and the strategy that produced it is read from the lot

### Requirement: The anchor is chosen per lot as MRP or ASP

A cost basis whose strategy depends on an anchor SHALL name that anchor explicitly as either `MRP` (the batch's recorded maximum retail price) or `ASP` (the product's observed marketplace/online selling price). Strategies that need no anchor (a flat per-unit cost, a multiplier on an entered cost) SHALL NOT require one.

#### Scenario: MRP anchor reads the batch MRP

- **WHEN** a lot's cost basis anchors to MRP
- **THEN** the derivation uses each batch's recorded MRP as the anchor value

#### Scenario: ASP anchor reads the product's online price

- **WHEN** a lot's cost basis anchors to ASP
- **THEN** the derivation uses each product's observed online price as the anchor value

### Requirement: Each strategy derives a per-unit cost

The engine SHALL derive a per-unit cost from the lot's cost basis by its strategy, computed exactly in integer paise (parameters stored as scaled integers, ratios evaluated in arbitrary precision and rounded half-up to the paise), never in floating point:

- **FLAT_PER_UNIT** — every unit costs the lot's stated flat per-unit cost.
- **PERCENT_OF_ANCHOR** — cost is the given percentage of the anchor value.
- **MRP_RATE_RANGE** — cost is the amount configured for the MRP band the item's MRP falls in (`min` inclusive, `max` exclusive, an open-topped final band).
- **MULTIPLIER** — cost is the multiplier applied to a chosen base: an entered per-unit cost, the anchor value, or the manifest stated value.

#### Scenario: Flat per unit

- **WHEN** a lot's basis is FLAT_PER_UNIT at a stated per-unit cost
- **THEN** every product received into the lot is costed at that flat figure

#### Scenario: A percentage of the anchor

- **WHEN** a lot's basis is PERCENT_OF_ANCHOR at 30% anchored to MRP, and a product's MRP is known
- **THEN** the product's per-unit cost is 30% of that MRP, rounded to the paise

#### Scenario: A cost band by MRP

- **WHEN** a lot's basis is MRP_RATE_RANGE and a product's MRP falls inside a configured band
- **THEN** the product's per-unit cost is that band's cost

#### Scenario: A multiplier on a base

- **WHEN** a lot's basis is MULTIPLIER at 1.25× a chosen base and that base is known
- **THEN** the product's per-unit cost is 1.25 times the base, rounded to the paise

### Requirement: Cost pins when its anchor is known, and is uncosted until then

A product's cost SHALL be pinned to its batch the moment the inputs its strategy needs are known, and the batch SHALL remain uncosted — an explicit state distinct from a cost of zero — until then. A flat per-unit cost, or a multiplier on an entered cost, is known at creation and SHALL pin then; an MRP-anchored cost SHALL pin when the MRP is recorded; an ASP-anchored cost SHALL pin when the online price is observed. An uncosted batch SHALL remain sellable and contribute zero cost of goods sold, unchanged from today.

#### Scenario: An MRP-anchored cost pins at counting

- **WHEN** a lot anchors to MRP and a product is received but its MRP is not yet recorded
- **THEN** its batch is uncosted; and when the MRP is later recorded, the cost is derived and pinned then

#### Scenario: A flat cost pins immediately

- **WHEN** a lot's basis is FLAT_PER_UNIT and a product is received
- **THEN** its batch is costed at once, without waiting for any anchor

#### Scenario: An item outside every rate band stays uncosted

- **WHEN** a lot's basis is MRP_RATE_RANGE and a product's MRP falls in no configured band
- **THEN** its batch is left uncosted and flagged, rather than given a guessed cost

### Requirement: The amount paid is a cross-check, not the cost driver

For a lot with a declared cost basis the amount paid SHALL NOT drive the cost. The system SHALL instead compare the sum of the derived line costs against the amount paid and record the variance, flagging a mismatch beyond a small tolerance for reporting. The mismatch SHALL NOT block receiving, costing, or pricing.

#### Scenario: A variance is recorded, not enforced

- **WHEN** the derived costs of a cost-basis lot sum to more or less than the amount paid
- **THEN** the variance is reported and flagged, and the lot's products are still costed and priceable

### Requirement: Editing the cost basis is guarded by the lot freeze rule

The cost basis SHALL be settable at lot creation and editable through the lot's update path, guarded by the same freeze rule as every other lot edit: once any stock from the lot has been consumed the basis SHALL NOT change (rejected as a conflict), because it would rewrite the recorded cost of goods already sold. While the lot is still editable, changing the basis SHALL re-derive and re-pin the cost of every not-yet-consumed batch in the lot. Missing or invalid parameters for the chosen strategy SHALL be rejected.

#### Scenario: Editing a frozen lot's basis is refused

- **WHEN** stock from a lot has been consumed and its cost basis is edited
- **THEN** the edit is rejected as a conflict and nothing changes

#### Scenario: Editing re-costs the lot's stock

- **WHEN** an editable lot's cost basis is changed
- **THEN** every not-yet-consumed batch of the lot is re-derived and re-pinned to the new basis

#### Scenario: Incomplete parameters are refused

- **WHEN** a cost basis is set without the parameters its strategy requires (for example a percentage strategy with no anchor)
- **THEN** the request is rejected with a message naming what is missing
