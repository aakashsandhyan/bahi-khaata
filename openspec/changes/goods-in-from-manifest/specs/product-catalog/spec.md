## MODIFIED Requirements

### Requirement: MRP is recorded against the goods received

Maximum retail price SHALL be recorded on the batch, because the same product can arrive in successive lots bearing different printed MRPs. A product SHALL expose the MRP of its most recently received batch for display and labelling. Where goods carry no printed MRP, an estimated retail value SHALL be recorded in its place and identified as an estimate.

MRP SHALL be optional at the point stock is received, because a supplier's manifest never carries the printed retail price and goods routinely arrive before anyone has read one. A batch without a recorded MRP SHALL be a legitimate recorded state rather than an error or a value of zero, and the product SHALL NOT be sellable while it persists.

MRP SHALL NOT be used as the weight for apportioning a lot's cost. A manifest may state a marketplace selling price, a supplier cost, or nothing at all, and none of those is a printed MRP.

#### Scenario: MRP is captured per batch

- **WHEN** a batch is received
- **THEN** the MRP printed on those goods is recorded against that batch

#### Scenario: Successive batches may differ in MRP

- **WHEN** a batch of a product is received bearing an MRP different from an earlier batch
- **THEN** both batches retain their own recorded MRP

#### Scenario: The product reports the most recent MRP

- **WHEN** a product's MRP is requested for display
- **THEN** the MRP of its most recently received batch is returned

#### Scenario: An estimated value is distinguishable from a printed one

- **WHEN** goods carry no printed MRP and an estimated retail value is recorded
- **THEN** the recorded value is identified as an estimate rather than a printed MRP

#### Scenario: Stock may be received before any MRP is known

- **WHEN** a batch is received from a manifest that states no retail price
- **THEN** the batch is recorded with no MRP
- **AND** this is not reported as an error

#### Scenario: A batch without an MRP holds its product off the floor

- **WHEN** a batch has no recorded MRP
- **THEN** the product is not sellable from that batch, however certain anyone is of its price

## ADDED Requirements

### Requirement: A product records what it last sold for online

A product MAY record the price its units last sold for on an online marketplace, together with which marketplace and the date the price was observed. The marketplace and the observation date SHALL be recorded whenever a price is, because a bare figure with neither cannot be judged.

This is an input to deciding a shelf price and never an authority for one. It SHALL NOT satisfy any requirement that calls for an MRP.

#### Scenario: An observed price is recorded with its source and date

- **WHEN** an online price is recorded for a product
- **THEN** the marketplace and the date observed are recorded with it

#### Scenario: A price without a marketplace or a date is refused

- **WHEN** an online price is recorded without a marketplace, or without an observation date
- **THEN** it is refused

#### Scenario: A newer observation replaces an older one

- **WHEN** an online price is recorded for a product that already has one observed earlier
- **THEN** the newer price, marketplace and date replace the older

#### Scenario: An older observation does not overwrite a newer one

- **WHEN** an online price is recorded bearing an observation date earlier than the one already held
- **THEN** the held price is unchanged

#### Scenario: An online price never makes a product sellable

- **WHEN** a product has an online price, a selling price, and no recorded MRP
- **THEN** it is not sellable
