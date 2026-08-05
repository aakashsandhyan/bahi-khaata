# product-catalog

## Purpose

The shop needs one place to browse the products it knows about and see which of them it has actually
found. A product enters the catalogue the moment a manifest is read — a name, a category, and a
marketplace reference, nothing more — and becomes real only when someone physically encounters it: a
scannable code mapped onto it, or a unit counted into a batch. So the catalogue always holds two
kinds of product, **found** and **on-paper**, and the on-paper ones are exactly the goods a delivery
still owes that nobody has laid hands on.

This capability is the shared product finder: search by name, narrow by found/on-paper and by
department, and open a product to price it, view its stock and codes, or hand it off to be counted.
"Found" is derived, never stored — a product is found when it has a counted batch or a barcode whose
origin is not the marketplace reference; on-paper when it has neither.
## Requirements
### Requirement: Counting is a hand-off, not performed in the catalogue
Counting SHALL NOT be performed by the catalogue capability, because counting belongs to a box and an open lot. The catalogue SHALL act only as the product data behind the picker: it identifies a product and signals intent to count, so the separate product-centric counting flow can attach to it. The picker itself is now Item detail's Count entry, which loads the open deliveries and feeds `ProductCountPane` unchanged; the deleted Catalog screen SHALL NOT be its home. The catalogue itself SHALL NOT record any count.

#### Scenario: Count is handed off from Item detail
- **WHEN** count is chosen for a product via Item detail's Count entry
- **THEN** the product is selected and counting intent is signalled to `ProductCountPane` scoped to a chosen open lot, and no count is recorded by the catalogue capability

#### Scenario: The catalogue is a reusable product finder
- **WHEN** another flow needs a product chosen by name or status
- **THEN** it can select through the same catalogue data (`catalog.browse`) rather than building its own finder

### Requirement: A product records when its label was printed
A product SHALL carry a label-printed marker, unset until a label for it prints successfully and
set at that moment. The marker SHALL let the catalogue and the bulk-print screen distinguish
shelf products that still need a label from those already labelled.

#### Scenario: Unlabelled after pricing without printing
- **WHEN** a product is priced and shelved but its label is not printed
- **THEN** its label-printed marker is unset and it appears among products awaiting a label

#### Scenario: Marked once a label prints
- **WHEN** a label for the product prints successfully
- **THEN** its label-printed marker is set and it no longer appears among products awaiting a
  label

#### Scenario: Reprinting an already-labelled product
- **WHEN** the user reprints a label for a product already marked
- **THEN** the reprint is allowed and the marker stays set

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
