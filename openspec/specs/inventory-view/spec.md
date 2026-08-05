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
The Inventory screen SHALL let the operator narrow the rows by condition, bin, lot, aging bucket, and a free-text search over product identity, and SHALL show totals — total units, total cost value, and total retail value — computed over the currently filtered set of rows, not the full table. Combining filters SHALL narrow the set further. The totals footer SHALL appear only in the On floor scope, because it sums stock, cost, and retail value that on-paper and All scopes do not carry; the free-text search and the department filter SHALL remain available in every scope. Condition, bin, lot, and aging-bucket filters — which read stock-only fields — SHALL apply in the On floor scope.

#### Scenario: A filter narrows the totals
- **WHEN** the operator applies a condition filter that removes some rows in the On floor scope
- **THEN** the total units, total cost value, and total retail value recompute over only the remaining rows

#### Scenario: Search matches product identity
- **WHEN** the operator types a term into the search field
- **THEN** only rows whose product identity matches the term remain, and (in the On floor scope) the totals reflect that subset

#### Scenario: Aging filter selects a bucket
- **WHEN** the operator selects an aging bucket in the On floor scope
- **THEN** only rows whose age falls in that bucket remain, and the totals reflect that subset

#### Scenario: Totals footer is hidden outside On floor
- **WHEN** the operator switches to the On paper or All scope
- **THEN** no totals footer is shown, while the free-text search and department filter continue to operate

### Requirement: Client-side CSV export
The Inventory screen SHALL export the currently displayed rows to CSV entirely on the client from the rows already loaded in memory, with no additional server round trip. The export SHALL reflect the current filtered set and the table's columns.

#### Scenario: Export reflects the filtered set
- **WHEN** the operator applies filters and then triggers CSV export
- **THEN** the downloaded CSV contains exactly the currently filtered rows and their columns, produced without a further server request

### Requirement: Scope control
The Inventory screen SHALL offer a scope control with three settings — On floor, On paper, and All — that swaps both the dataset and the column set rather than filtering rows of one table. On floor SHALL keep `GET /api/inventory` and render the stock table. On paper and All SHALL ride the catalog browse API (`GET /api/catalog`, `catalog.browse`) and render the catalog column set (status badge, priced/label markers, counted-of-expected units, and department), NOT the stock columns. An on-paper product — known from a manifest but never counted — has no stock, cost, or age, so those scopes SHALL NOT fabricate zero stock, zero cost, or a zero age for it; the catalog columns SHALL be shown honestly in their place. The totals footer SHALL appear only in the On floor scope. The free-text search and the department filter SHALL work in every scope. On paper SHALL list only products a delivery still owes that nobody has counted; All SHALL list both found and on-paper products.

#### Scenario: On paper rides the catalog browse API
- **WHEN** the operator selects the On paper scope
- **THEN** the rows are sourced from the catalog browse API (`catalog.browse`), not from the inventory aggregate, and only manifest-known, uncounted products are listed

#### Scenario: On paper and All render catalog columns
- **WHEN** the operator views the On paper or All scope
- **THEN** the table renders the catalog column set (status, priced/label markers, counted-of-expected, department) and NOT the stock columns (Condition, Lot, Bin, On hand, Cost, Price, Margin, Age)

#### Scenario: No fabricated stock zeros for on-paper rows
- **WHEN** an on-paper product with no counted stock is listed
- **THEN** the view SHALL NOT show a fabricated zero on-hand quantity, zero cost, or zero age for it, presenting the catalog columns instead

#### Scenario: All lists found and on-paper together
- **WHEN** the operator selects the All scope
- **THEN** both found and on-paper products are listed via the catalog browse API, ordered by name, with the catalog column set

#### Scenario: Search and department filter work in every scope
- **WHEN** the operator applies a free-text search or a department filter
- **THEN** the filter narrows the rows in whichever scope is active — On floor, On paper, or All

#### Scenario: Totals footer only in On floor
- **WHEN** the operator is in the On floor scope
- **THEN** the totals footer (units, cost value, retail value) is shown, and it is hidden in On paper and All
