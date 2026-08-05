## MODIFIED Requirements

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

## ADDED Requirements

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
