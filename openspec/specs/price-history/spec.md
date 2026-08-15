## Purpose

The append-only journal of selling-price changes, written at the single pricing choke point and immutable at the database layer. Established by the palletworks-inventory change.

## Requirements

### Requirement: Every price change is journaled at the pricing choke point
Every selling-price set or change SHALL be journaled to `price_history` from inside `ProductPricing.setSellingPrice` — the single choke point through which every price path funnels — so no caller journals on its own and no caller can bypass the record. The choke point SHALL read the product's current price before the set and append one `price_history` row carrying the old price, the new price, and the change time. On the product's first-ever price set the old price SHALL be recorded as NULL. A set whose new price equals the product's current price SHALL be treated as a no-op and SHALL NOT be journaled, so a re-price that only corrects an MRP or quantity writes no history row.

#### Scenario: Workbench single reprice journals
- **WHEN** a product is repriced through the pricing workbench single-item reprice
- **THEN** a `price_history` row is appended with the old and new prices via the choke point

#### Scenario: Bulk reprice journals
- **WHEN** products are repriced through the pricing workbench bulk reprice
- **THEN** each changed product gets a `price_history` row appended via the choke point

#### Scenario: saveExisting journals
- **WHEN** a scanned, already-counted product is priced through `ShelfPricing.saveExisting`
- **THEN** its price set is journaled to `price_history` via the choke point

#### Scenario: saveManual journals
- **WHEN** a hand-keyed product is priced through `ShelfPricing.saveManual`
- **THEN** its price set is journaled to `price_history` via the choke point

#### Scenario: Catalog inline edit journals
- **WHEN** a product's selling price is edited inline through the catalog controller
- **THEN** that change is journaled to `price_history` via the choke point

#### Scenario: First set records a NULL old price
- **WHEN** a product with no prior selling price is priced for the first time
- **THEN** the appended `price_history` row records the old price as NULL and the new price as the set value

#### Scenario: An unchanged-price set is not journaled
- **WHEN** the choke point is called with a new price equal to the product's current price
- **THEN** no `price_history` row is appended, because no price change occurred

### Requirement: Price history is append-only
The `price_history` table SHALL be append-only, guarded by database triggers that reject any UPDATE or DELETE against it, mirroring the stock ledger's immutability. Being immutable, it SHALL carry only a creation time and no updated-at.

#### Scenario: Updates are rejected
- **WHEN** any statement attempts to UPDATE a `price_history` row
- **THEN** the trigger rejects it and the row is unchanged

#### Scenario: Deletes are rejected
- **WHEN** any statement attempts to DELETE a `price_history` row
- **THEN** the trigger rejects it and the row remains

### Requirement: Price history read for a product
The price history for a product SHALL be readable newest-first as part of the item detail composition, each entry exposing the old price (NULL for the first-ever set), the new price, and the change time.

#### Scenario: Reading a product's price history
- **WHEN** the item detail payload is composed for a product with recorded price changes
- **THEN** its price history is returned newest-first, each entry carrying old price, new price, and change time
