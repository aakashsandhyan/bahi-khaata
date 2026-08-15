## MODIFIED Requirements

### Requirement: Counting is a hand-off, not performed in the catalogue
Counting SHALL NOT be performed by the catalogue capability, because counting belongs to a box and an open lot. The catalogue SHALL act only as the product data behind the picker: it identifies a product and signals intent to count, so the separate product-centric counting flow can attach to it. The picker itself is now Item detail's Count entry, which loads the open deliveries and feeds `ProductCountPane` unchanged; the deleted Catalog screen SHALL NOT be its home. The catalogue itself SHALL NOT record any count.

#### Scenario: Count is handed off from Item detail
- **WHEN** count is chosen for a product via Item detail's Count entry
- **THEN** the product is selected and counting intent is signalled to `ProductCountPane` scoped to a chosen open lot, and no count is recorded by the catalogue capability

#### Scenario: The catalogue is a reusable product finder
- **WHEN** another flow needs a product chosen by name or status
- **THEN** it can select through the same catalogue data (`catalog.browse`) rather than building its own finder

## REMOVED Requirements

### Requirement: Name-filtered product listing
**Reason**: The name-filtered browse behavior moved wholesale into the Inventory scope control; the standalone Catalog screen is deleted (D9, D10) and the browse is now surfaced by Inventory's On paper / All scopes.
**Migration**: Behavior now lives in `inventory-view` → "Scope control" (free-text search over `catalog.browse` in every scope). The backend `catalog.browse` listing endpoint is unchanged; only its sole caller and its spec home moved to Inventory.

### Requirement: Found versus on-paper status
**Reason**: The found/on-paper distinction is now surfaced by the Inventory scope control (On floor / On paper / All), where On paper lists manifest-known, uncounted products; the Catalog screen that presented it is deleted (D10).
**Migration**: Behavior now lives in `inventory-view` → "Scope control". The derivation (found = counted batch or non-marketplace code; on-paper = neither) is unchanged in `catalog.browse` and is depended on by the On paper scope.

### Requirement: On-paper products surfaced first
**Reason**: Choosing between on-paper only, found only, and all products is now the Inventory scope control's three settings; the default-and-tabs UI moved off the deleted Catalog screen (D10).
**Migration**: Behavior now lives in `inventory-view` → "Scope control" (On floor / On paper / All), which owns the on-paper/found/all choice.

### Requirement: Filtering by department
**Reason**: The department filter moved to the Inventory scope control, where it works in every scope alongside the free-text search (D2, D3, D10).
**Migration**: Behavior now lives in `inventory-view` → "Scope control" and the modified "Inventory filters and filtered totals" requirement.

### Requirement: Opening a product to its detail
**Reason**: Opening a product to its detail is owned by `item-detail`; the Catalog product panel is deleted and Item detail is reached from Inventory rows (including On paper / All), with on-paper detail now covered explicitly (D6, D10).
**Migration**: Behavior now lives in `item-detail` → "Item detail is opened with a product id" and "Item detail opens for on-paper products".

### Requirement: Setting a price from the catalogue
**Reason**: Setting a price is owned by `item-detail`'s reprice action, which routes through the single shelf-pricing choke point; the Catalog screen's set-price panel is deleted (D10).
**Migration**: Behavior now lives in `item-detail` → "Item detail actions reuse existing paths" (reprice via the existing shelf-pricing endpoint).
