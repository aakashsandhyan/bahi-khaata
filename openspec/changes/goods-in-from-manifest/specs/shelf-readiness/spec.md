## ADDED Requirements

### Requirement: Goods reach the floor only once priced, MRP-bearing and labelled

A product SHALL NOT be sellable until it has a selling price, a recorded MRP for the batch in hand, and a printed label. The label SHALL be the gate onto the shop floor.

#### Scenario: Unlabelled goods cannot be sold

- **WHEN** a product has a price and a recorded MRP but has not been labelled
- **THEN** it is not sellable

#### Scenario: A labelled product with both figures is sellable

- **WHEN** a product has a selling price, a recorded MRP, and a printed label
- **THEN** it is sellable

#### Scenario: The reason a product is not sellable is reportable

- **WHEN** a product is not sellable
- **THEN** which of price, MRP or label is missing is reportable

### Requirement: MRP is read off the goods and is never inferred from a price

MRP SHALL be recorded from the printed figure on the goods. No other figure SHALL be substituted for it — not a marketplace selling price, not a supplier cost, not an apportioned cost.

MRP is the printed legal ceiling and selling above it is unlawful. A marketplace price is one seller's asking price on one day and carries no legal standing.

#### Scenario: An observed online price does not satisfy the MRP requirement

- **WHEN** a product has an observed online price but no recorded MRP
- **THEN** it is not sellable
- **AND** the online price is not recorded as its MRP

#### Scenario: A looked-up MRP is marked an estimate

- **WHEN** an MRP is obtained by lookup rather than read off the goods
- **THEN** it is recorded and identified as an estimate

#### Scenario: Goods with no printed MRP remain unsellable until one is supplied

- **WHEN** goods carry no printed MRP and none has been supplied
- **THEN** the product is not sellable

### Requirement: A margin price needs the batch's cost, which is known at receipt

Setting a margin-based selling price SHALL require the batch's cost to be known. A batch costed from its pinned manifest cost is known the moment it is received, so its product MAY be priced without the lot being closed. Only an uncosted surplus — goods no manifest line named, carrying no stated cost — lacks a cost to compute a margin against.

#### Scenario: A manifest-costed product is priceable at receipt

- **WHEN** a product's batch is costed from its pinned manifest cost
- **THEN** a margin price may be set for it without the lot being closed

#### Scenario: An uncosted surplus has no cost to price a margin against

- **WHEN** a product's only batch is an uncosted surplus with no stated cost
- **THEN** a margin cannot be computed for it until a cost is decided

### Requirement: A label shows the MRP, our price, and the saving

A printed label SHALL show the recorded MRP, the selling price, and the difference between them expressed both in rupees and as a percentage.

The saving is the shop's proposition to the customer, and showing it against the printed MRP is what makes it checkable.

#### Scenario: The label carries all three figures

- **WHEN** a label is produced
- **THEN** it shows the recorded MRP, the selling price, and the saving in both rupees and percent

#### Scenario: A label cannot be produced without an MRP

- **WHEN** a label is requested for a product with no recorded MRP
- **THEN** it is refused

#### Scenario: A price above the recorded MRP is refused

- **WHEN** a selling price above the recorded MRP is set
- **THEN** it is refused, because selling above the printed maximum retail price is unlawful

### Requirement: A label carries the product's own code, and the lot is reachable through it

A label SHALL carry the product's existing barcode rather than a code minted per batch. The lot a labelled item came from SHALL be reachable by following the product to its batches and their lots.

#### Scenario: A scanned label resolves to a product

- **WHEN** a label's code is scanned
- **THEN** it resolves to the product

#### Scenario: A product's lots are reachable from it

- **WHEN** the origin of a product is requested
- **THEN** its batches and the lots and suppliers they came from are returned

#### Scenario: Which batch a scanned item belongs to is an attribution

- **WHEN** a product has stock from more than one lot on hand and a label is scanned
- **THEN** the batch is attributed by the consumption order rather than determined from the code
