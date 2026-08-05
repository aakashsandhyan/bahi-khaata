## Purpose

The stock-centric Inventory view and its aggregate API: what is on the floor, in which condition, where it sits, and what it is worth. Established by the palletworks-inventory change.

## Requirements

### Requirement: Aggregate inventory endpoint
The system SHALL expose `GET /api/inventory` backed by a single-pass, read-only native aggregate that returns one row per product × stock condition, rolled up across that product's lots. On-hand quantity SHALL be the sum of the product's `stock_ledger` movements for that condition. Each row SHALL carry: product identity, condition, a lot rollup (the single lot when exactly one lot backs the row, otherwise an "N lots" marker), bin(s), on-hand quantity, cost basis (from the batch allocated unit cost), selling price, margin (derived from price and cost basis), and age in whole days since the product's first `PURCHASE_RECEIPT` movement, computed as a UTC-day difference. A product × condition group whose net on-hand quantity is not greater than zero SHALL be excluded, so the table shows only stock physically on the floor.

#### Scenario: A product held in two conditions yields two rows
- **WHEN** a product has on-hand stock in both GOOD and DAMAGED condition
- **THEN** the aggregate returns two separate rows for that product, one per condition, each with its own cost basis, price, and margin

#### Scenario: Lot rollup collapses multiple lots
- **WHEN** a product × condition row is backed by stock from more than one lot
- **THEN** the lot column shows an "N lots" marker rather than a single lot reference, while a row backed by exactly one lot shows that lot

#### Scenario: Age is days since first receipt
- **WHEN** a row's product first received a `PURCHASE_RECEIPT` movement 45 days ago
- **THEN** the row reports an age of 45 whole days, measured as a UTC-day difference

#### Scenario: Zero-stock groups are excluded
- **WHEN** a product × condition group's ledger movements net to zero or below
- **THEN** that group SHALL NOT appear in the inventory rows

#### Scenario: Margin is derived from price and cost basis
- **WHEN** a row has a cost basis and a selling price
- **THEN** its margin is computed from those two figures and returned on the row, not stored separately

### Requirement: Inventory filters and filtered totals
The Inventory screen SHALL let the operator narrow the rows by condition, bin, lot, aging bucket, and a free-text search over product identity, and SHALL show totals — total units, total cost value, and total retail value — computed over the currently filtered set of rows, not the full table. Combining filters SHALL narrow the set further.

#### Scenario: A filter narrows the totals
- **WHEN** the operator applies a condition filter that removes some rows
- **THEN** the total units, total cost value, and total retail value recompute over only the remaining rows

#### Scenario: Search matches product identity
- **WHEN** the operator types a term into the search field
- **THEN** only rows whose product identity matches the term remain, and the totals reflect that subset

#### Scenario: Aging filter selects a bucket
- **WHEN** the operator selects an aging bucket
- **THEN** only rows whose age falls in that bucket remain, and the totals reflect that subset

### Requirement: Client-side CSV export
The Inventory screen SHALL export the currently displayed rows to CSV entirely on the client from the rows already loaded in memory, with no additional server round trip. The export SHALL reflect the current filtered set and the table's columns.

#### Scenario: Export reflects the filtered set
- **WHEN** the operator applies filters and then triggers CSV export
- **THEN** the downloaded CSV contains exactly the currently filtered rows and their columns, produced without a further server request
