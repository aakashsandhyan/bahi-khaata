## ADDED Requirements

### Requirement: Pricing starts from a lot
The pricing workbench SHALL begin by selecting one open lot, and all pricing in a session
SHALL be scoped to that lot so every product priced has a known FIFO unit cost. The selected
lot SHALL be changeable during the session.

#### Scenario: Selecting a lot to price against
- **WHEN** the user opens the pricing workbench and selects an open lot
- **THEN** the workbench scopes pricing to that lot and offers both to scan an item into it and
  to key one in by hand

#### Scenario: Changing the lot mid-session
- **WHEN** the user selects a different lot
- **THEN** the workbench rescopes to the new lot without losing any product already saved

### Requirement: Scanning an identifiable item versus keying one by hand
The workbench SHALL let the user add a product to the selected lot in two ways, and which path an
item takes is decided by whether it can be scanned to its record. Scanning an LSN or ASIN that is
mapped in the barcode table SHALL resolve the already-counted product and batch and open it for
pricing, creating no stock. Stock that cannot be scanned — its LSN/LPN reference lost, or never
counted — SHALL be keyed in by hand, which creates a product and a batch under the lot.

#### Scenario: Scanning an identifiable counted item
- **WHEN** the user scans an LSN or ASIN mapped in the barcode table
- **THEN** the workbench opens the resolved already-counted product for pricing with its unit
  cost from the lot's batch, creating no new stock

#### Scenario: Keying in a counted item whose LSN reference was lost
- **WHEN** a counted unit's LSN/LPN reference is lost, so it cannot be scanned, and the user keys
  it in by hand
- **THEN** the system creates a product and a batch under the lot for it, and the double-count
  against its orphaned counted batch is netted later by lot reconciliation

#### Scenario: Keying in never-counted stock
- **WHEN** the user keys in a name, quantity, and condition for stock never counted
- **THEN** the system creates the product and a batch under the selected lot and opens it for
  pricing

### Requirement: Uncosted stock is hand-priced
When a product's batch is uncosted — a manual batch created after the lot's cost was already
allocated — the workbench SHALL NOT suggest a selling price, and the user SHALL be able to enter
the selling price by hand. A margin-based suggestion SHALL appear only for costed stock.

#### Scenario: No suggestion for uncosted stock
- **WHEN** the user prices a product whose batch has no allocated cost
- **THEN** no price is suggested and the user enters the selling price manually

#### Scenario: Suggestion for costed stock
- **WHEN** the user prices a product whose batch is costed and chooses a category
- **THEN** a suggested price is shown from the category margin and the batch's unit cost

### Requirement: Missing manifested stock is not priceable
The workbench SHALL list only stock on hand — products with a costed batch in the lot. A
manifested item that was never counted SHALL NOT appear in the workbench; its cost is absorbed
into the found stock at lot close, and it is handled by receiving, not pricing.

#### Scenario: A never-counted manifest line is absent
- **WHEN** a lot has a manifested product that was never counted
- **THEN** that product does not appear among the lot's priceable items

### Requirement: Reconciling a lot writes off phantom stock
The workbench SHALL provide a lot-reconciliation step that computes phantom stock as the counted
quantity minus the quantity priced and shelved, and writes the phantom off as shrinkage with a
single append-only negative stock ledger movement. No existing ledger row SHALL be edited. This
nets the double-count created when lost-LSN units are re-entered manually rather than matched to
their original counted batch.

#### Scenario: Writing off phantom stock at reconciliation
- **WHEN** the user reconciles a lot whose counted quantity exceeds what has been priced and
  shelved
- **THEN** the difference is written off as shrinkage with an append-only negative ledger
  movement, recorded as a loss on the lot, and system stock equals the physical count

#### Scenario: Nothing to write off
- **WHEN** the user reconciles a lot whose counted quantity equals what was priced and shelved
- **THEN** no write-off is made

### Requirement: Category is chosen from the lot, price is suggested from its margin
Pricing SHALL offer the categories present in the selected lot as the category choices, falling
back to the full category list when the lot has none yet. A suggested selling price SHALL be
produced only once a category is chosen, from that category's target margin applied to the
product's lot unit cost. MRP SHALL be optional.

#### Scenario: Category drives the suggested price
- **WHEN** the user chooses a category for a product being priced
- **THEN** the workbench shows a suggested selling price computed from the category's target
  margin and the product's lot unit cost

#### Scenario: No category chosen yet
- **WHEN** no category has been chosen for the product
- **THEN** no selling price is suggested

#### Scenario: MRP is optional
- **WHEN** the user saves a product with no MRP entered
- **THEN** the product is priced and saved without an MRP

### Requirement: MRP is confirmed at pricing
When the user enters or accepts an MRP at pricing, the batch's MRP SHALL be recorded as confirmed
(not an estimate), resolving any estimate carried from counting. Only a confirmed MRP allows the
label to show a struck MRP and a saving; an absent MRP yields a price-only label.

#### Scenario: Confirming an MRP at pricing
- **WHEN** the user enters or accepts an MRP while pricing a product
- **THEN** the batch's MRP is recorded as confirmed, so the printed label may strike it and show
  the saving

### Requirement: Saving prices and barcodes the product; only manual entry moves stock
Saving a priced product SHALL set its category and selling price and mint a BBZ barcode if the
product has none. When the product was keyed in by hand (never-counted or lost-reference stock),
saving SHALL also move the captured quantity onto the shelf with an append-only stock ledger
receipt. When the product was resolved by scanning an already-counted item, saving SHALL write no
stock movement, because the stock was already received at counting. Selling price SHALL be set
only through the product's sanctioned price mutation; receiving stock SHALL never change it.

#### Scenario: Saving a scanned, already-counted product writes no stock movement
- **WHEN** the user saves a product resolved by scanning an already-counted item
- **THEN** its selling price and category are set and a BBZ barcode is assigned if it had none,
  and no stock ledger movement is written

#### Scenario: Saving a hand-keyed product moves its quantity onto the shelf
- **WHEN** the user saves a hand-keyed product with a category, selling price, and quantity
- **THEN** the product is priced and barcoded, and the quantity is moved onto the shelf as an
  append-only stock ledger receipt

#### Scenario: A product that already has a barcode keeps it
- **WHEN** the user saves a product that already has a BBZ barcode
- **THEN** no new barcode is minted and the existing code is kept

### Requirement: Offer to print a label after saving
After a product is saved, the workbench SHALL offer to print its label, queuing a self-contained
print job for it. Declining SHALL leave the product on the shelf unlabelled, printable later in
bulk.

#### Scenario: Printing on save
- **WHEN** the user saves a product and chooses to print
- **THEN** a self-contained label job for that product is queued

#### Scenario: Declining to print on save
- **WHEN** the user saves a product and declines to print
- **THEN** the product is on the shelf with no label printed, and appears in the bulk-print list
