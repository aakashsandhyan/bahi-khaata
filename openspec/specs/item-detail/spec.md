## Purpose

The per-product Item detail view: KPIs, movement log, price history, batches with bin editing, and actions that reuse existing write paths. Established by the palletworks-inventory change.

## Requirements

### Requirement: Item detail is opened with a product id
The Item detail view SHALL be opened for a specific product, carried as a product id. `App` SHALL hold a `detailProductId` alongside its `view` and expose an `onOpenItem(id)` callback that switches to the detail view for that product. The view SHALL be reachable from any Inventory row in any scope, using the same state-switch mechanism with no routing library.

#### Scenario: Opening from an Inventory row
- **WHEN** the operator activates a row in the Inventory table
- **THEN** the app switches to the Item detail view carrying that row's product id and renders that product's detail

#### Scenario: Opening from an on-paper row
- **WHEN** the operator activates an On-paper scope row
- **THEN** the same `onOpenItem` mechanism opens the detail view for that product

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

### Requirement: Item detail category editing
The Item detail actions SHALL let the operator change a product's category (department) through a new minimal endpoint `PATCH /api/products/{id}/category` on the existing `ProductController`, which SHALL call `Product.setCategory`. This SHALL be a plain reclassification: it SHALL NOT require a batch, SHALL NOT set or change a price, and SHALL NOT move stock, so it SHALL NOT reuse the shelf-pricing save path (which demands a `batchId` and reprices) nor the bulk-print label edit (which edits a queued label entry, not the product). The endpoint SHALL validate the category the same way the existing product edits do.

#### Scenario: Editing the category persists via the new endpoint
- **WHEN** the operator changes a product's category from Item detail
- **THEN** the change is saved through `PATCH /api/products/{id}/category` calling `Product.setCategory`, and the product shows the new category on reload

#### Scenario: Category edit does not reprice or move stock
- **WHEN** the operator changes only the category
- **THEN** no price is set or changed and no stock is moved, and the shelf-pricing endpoint is not called

### Requirement: Item detail counting-grid entry
The Item detail actions rail SHALL offer a Count entry that opens the existing `ProductCountPane` unchanged. Because Item detail carries no screen-level delivery selector, the entry SHALL load the open deliveries (`unpacking.deliveries`, filtered to those not closed) into an inline picker and SHALL preselect a lot when exactly one open lot owes the product. The chosen lot SHALL supply the `lotId` that `ProductCountPane` requires; no product-scoped counting endpoint SHALL be added, because `ProductCountPane` already returns an empty grid for a lot that owes the product nothing. A count SHALL always be attached to a box in an open lot — the Count entry SHALL NOT record a count with no lot chosen.

#### Scenario: Count entry opens the count pane with a picked lot
- **WHEN** the operator activates the Count entry on Item detail and picks an open delivery
- **THEN** `ProductCountPane` opens for that product scoped to the chosen lot, using the existing count path unchanged

#### Scenario: Single open lot owing the product is preselected
- **WHEN** exactly one open lot owes the product
- **THEN** that lot is preselected in the inline picker so the operator can count without first choosing

#### Scenario: A lot that owes nothing degrades cleanly
- **WHEN** the operator picks an open lot that owes the product nothing
- **THEN** `ProductCountPane` shows an empty grid rather than an error, and no product-scoped endpoint is required

### Requirement: Item detail opens for on-paper products
Item detail SHALL be openable for an on-paper product — one known from a manifest but never counted — reached via `onOpenItem(id)` from an On paper or All Inventory row. `GET /api/inventory/product/{id}` (`inventory.detail`) SHALL resolve the product's name, category, and barcodes for an uncounted product, and the stock-dependent sections (batches, movement log, price history) SHALL render their own honest empty states rather than fabricated rows. Item detail SHALL be a valid landing for a product the operator is about to count, and the expected-versus-counted gap SHALL remain a list-row fact in the On paper scope, NOT be duplicated onto the detail payload.

#### Scenario: Opening an on-paper product shows identity with empty stock sections
- **WHEN** the operator opens Item detail for an on-paper product from an On paper or All row
- **THEN** the header shows the product's name, category, and barcodes, and the batches, movement log, and price history sections render honest empty states with no fabricated rows

#### Scenario: The Count entry is the point of opening an on-paper product
- **WHEN** Item detail is opened for an on-paper product
- **THEN** the Count entry is available so the operator can count the product into an open lot's box

#### Scenario: Expected-versus-counted stays a list fact
- **WHEN** an on-paper product is opened in Item detail
- **THEN** the expected-versus-counted gap is not restated on the detail payload, remaining a row-level fact in the On paper scope
