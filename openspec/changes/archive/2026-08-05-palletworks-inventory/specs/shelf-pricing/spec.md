## MODIFIED Requirements

### Requirement: Saving prices and barcodes the product; only manual entry moves stock
Saving a priced product SHALL set its category and selling price and mint a BBZ barcode if the
product has none. When the product was keyed in by hand (never-counted or lost-reference stock),
saving SHALL also move the captured quantity onto the shelf with an append-only stock ledger
receipt. When the product was resolved by scanning an already-counted item, saving SHALL write no
stock movement, because the stock was already received at counting. Selling price SHALL be set
only through the product's sanctioned price mutation; receiving stock SHALL never change it.
Every selling-price set or change made through that sanctioned mutation SHALL be journaled to
price history from the single pricing choke point, regardless of caller — workbench save, manual
save, single or bulk reprice, and catalog inline edit all funnel through it — so no save path can
set a price without recording the change. A pricing save MAY also set the batch's bin.

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

#### Scenario: Saving a price journals the change
- **WHEN** the user saves a product with a selling price different from its current one
- **THEN** the price is set through the single pricing choke point and a price-history row is
  recorded for the change

#### Scenario: Pricing save sets the batch bin
- **WHEN** the user supplies a bin while saving a priced product
- **THEN** the batch is saved with that bin on the same save
