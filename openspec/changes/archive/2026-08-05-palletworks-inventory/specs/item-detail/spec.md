## ADDED Requirements

### Requirement: Item detail is opened with a product id
The Item detail view SHALL be opened for a specific product, carried as a product id. `App` SHALL hold a `detailProductId` alongside its `view` and expose an `onOpenItem(id)` callback that switches to the detail view for that product. The view SHALL be reachable both from an Inventory row and from the Catalog product panel, using the same state-switch mechanism with no routing library.

#### Scenario: Opening from an Inventory row
- **WHEN** the operator activates a row in the Inventory table
- **THEN** the app switches to the Item detail view carrying that row's product id and renders that product's detail

#### Scenario: Opening from the Catalog panel
- **WHEN** the operator opens item detail from the Catalog product panel
- **THEN** the app switches to the Item detail view for that product using the same `onOpenItem` mechanism

### Requirement: Item detail composition
The Item detail view SHALL compose one product's full story from `GET /api/inventory/product/{id}`: a header with condition and barcodes; KPI cells (cost basis, price, margin, sold/received); a movement log sourced from the stock ledger; a price history section listing changes newest-first, each showing the old price and the new price; and a per-batch list. A price-history entry that is a first-ever price set — with no prior price — SHALL render its old-price side as an explicit empty/first-set marker rather than a zero or blank number.

#### Scenario: KPI cells are shown
- **WHEN** the Item detail view loads a product
- **THEN** it shows cost basis, price, margin, and sold/received KPI cells for that product

#### Scenario: Movement log comes from the ledger
- **WHEN** the Item detail view loads a product with ledger movements
- **THEN** the movement log lists those movements from the stock ledger

#### Scenario: Price history is newest-first with old to new
- **WHEN** the Item detail view loads a product with two or more price changes
- **THEN** the price history section lists them newest-first, each showing the transition from the old price to the new price

#### Scenario: First-ever price set renders with no old price
- **WHEN** a price-history entry is the product's first price set and carries no prior price
- **THEN** its old-price side renders as an explicit first-set marker, not a zero or blank value

### Requirement: Item detail per-batch bin editing
The per-batch list SHALL show each batch's bin and let the operator edit it in place, persisting through `PUT /api/inventory/batch/{id}/bin`. The edit SHALL be an isolated bin change and SHALL NOT reprice or move stock.

#### Scenario: Editing a batch bin persists
- **WHEN** the operator edits a batch's bin in the per-batch list and the page is later reloaded
- **THEN** the batch shows the edited bin, saved via the bin endpoint, with no change to price or stock

### Requirement: Item detail actions reuse existing paths
The Item detail actions rail SHALL offer a reprice action and a label reprint action. Reprice SHALL call the existing shelf-pricing endpoint — the same choke point every other price set uses — and SHALL NOT introduce a second price-write path. The label action SHALL queue a label reprint for the product.

#### Scenario: Reprice reuses the shelf-pricing endpoint
- **WHEN** the operator repricing from Item detail submits a new price
- **THEN** the change goes through the existing shelf-pricing endpoint, not a new write path unique to item detail

#### Scenario: Queueing a label reprint
- **WHEN** the operator triggers the label reprint action
- **THEN** a label job for that product is queued

### Requirement: Item detail loading and error states
The Item detail view SHALL present loading and error states consistent with the other dashboard screens: a loading indicator while the detail payload is in flight, and a clear error state when the fetch fails, without a blank or broken screen.

#### Scenario: Loading state while fetching
- **WHEN** the detail payload has not yet arrived
- **THEN** the view shows a loading indicator consistent with other screens

#### Scenario: Error state on fetch failure
- **WHEN** the detail fetch fails
- **THEN** the view shows a clear error state rather than a blank or broken screen
