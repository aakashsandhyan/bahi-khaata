## MODIFIED Requirements

### Requirement: Item detail is opened with a product id
The Item detail view SHALL be opened for a specific product, carried as a product id. `App` SHALL hold a `detailProductId` alongside its `view` and expose an `onOpenItem(id)` callback that switches to the detail view for that product. The view SHALL be reachable from any Inventory row in any scope, using the same state-switch mechanism with no routing library.

#### Scenario: Opening from an Inventory row
- **WHEN** the operator activates a row in the Inventory table
- **THEN** the app switches to the Item detail view carrying that row's product id and renders that product's detail

#### Scenario: Opening from an on-paper row
- **WHEN** the operator activates an On-paper scope row
- **THEN** the same `onOpenItem` mechanism opens the detail view for that product

## ADDED Requirements

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
