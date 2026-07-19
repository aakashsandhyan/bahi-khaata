## ADDED Requirements

### Requirement: Product identity is stable and globally unique

Every product SHALL be identified by a UUIDv4 assigned at creation. The identifier SHALL NOT change for the lifetime of the product and SHALL NOT be reused, so that records generated independently on different machines can never collide.

#### Scenario: Identifier assigned at creation

- **WHEN** a product is created
- **THEN** the system assigns it a UUIDv4 identifier
- **AND** that identifier is distinct from every other product identifier

#### Scenario: Identifier survives edits

- **WHEN** any attribute of an existing product is changed
- **THEN** the product's identifier remains unchanged

### Requirement: Products are resolvable by barcode

A barcode SHALL resolve to at most one product. The system SHALL resolve a scanned code to its product, and SHALL report a code it does not recognise as unknown rather than creating a product implicitly.

#### Scenario: Known barcode resolves

- **WHEN** a barcode assigned to a product is looked up
- **THEN** the system returns that product

#### Scenario: Unknown barcode is reported, not created

- **WHEN** a barcode that is not assigned to any product is looked up
- **THEN** the system reports the code as unknown
- **AND** no product is created

#### Scenario: A barcode cannot be assigned twice

- **WHEN** a barcode already assigned to one product is assigned to a different product
- **THEN** the system rejects the assignment

### Requirement: Internally generated barcodes cannot collide with manufacturer codes

For stock arriving without a usable manufacturer barcode, the system SHALL generate a Code 128 barcode carrying a reserved prefix. The prefix SHALL be one that manufacturer-assigned codes cannot produce, so an internal code is always distinguishable from an external one.

#### Scenario: Generated code carries the reserved prefix

- **WHEN** the system generates a barcode for a product with no manufacturer code
- **THEN** the generated code is Code 128
- **AND** it begins with the reserved internal prefix

#### Scenario: Generated codes are unique

- **WHEN** barcodes are generated for two different products
- **THEN** the two generated codes differ

#### Scenario: Internal codes are distinguishable from manufacturer codes

- **WHEN** any barcode is examined
- **THEN** the system can determine whether it was internally generated or manufacturer-assigned

### Requirement: Every product belongs to a category from a governed set

Every product SHALL carry a category drawn from a fixed, governed set of categories, and the database SHALL reject a category outside that set. The category is not free text: an unconstrained string would let the same category be spelled several ways, and per-category configuration such as target margin would then attach to inconsistent keys.

#### Scenario: A product takes a category from the governed set

- **WHEN** a product is stored with a category from the governed set
- **THEN** the product is stored successfully with that category

#### Scenario: A category outside the set is rejected at the database

- **WHEN** a product is stored with a category value not in the governed set, including directly against the database
- **THEN** the database rejects it

### Requirement: Category-varying attributes are stored without schema change

Products SHALL store fields common to all products as first-class columns, and category-specific attributes as a single JSON document. Adding a product carrying attribute names never used before SHALL NOT require a schema migration.

#### Scenario: Category-specific attributes round-trip

- **WHEN** a product is stored with category-specific attributes such as serial number and warranty period
- **THEN** retrieving that product returns those attributes with their values unchanged

#### Scenario: Products without category-specific attributes are valid

- **WHEN** a product is stored with no category-specific attributes
- **THEN** the product is stored successfully

#### Scenario: Unfamiliar attribute names require no migration

- **WHEN** a product is stored carrying attribute names never previously used
- **THEN** the product is stored successfully without a schema change

### Requirement: Selling price belongs to the product, never to a batch

Selling price SHALL be an attribute of the product. Receiving stock at a different cost SHALL NOT change the selling price. Price changes SHALL occur only as an explicit, deliberate action.

#### Scenario: New stock at a different cost leaves price untouched

- **WHEN** a new batch of an existing product is received at a cost different from previous batches
- **THEN** the product's selling price is unchanged

#### Scenario: Units from different batches carry the same price

- **WHEN** the selling price of a product is requested
- **THEN** a single price is returned regardless of which batch a physical unit came from

#### Scenario: Price changes only when set explicitly

- **WHEN** a product's selling price is explicitly set to a new value
- **THEN** the product's selling price becomes that value

### Requirement: MRP is recorded against the goods received

Maximum retail price SHALL be recorded on the batch, because the same product can arrive in successive lots bearing different printed MRPs. A product SHALL expose the MRP of its most recently received batch for display and labelling. Where goods carry no printed MRP, an estimated retail value SHALL be recorded in its place and identified as an estimate.

#### Scenario: MRP is captured per batch

- **WHEN** a batch is received
- **THEN** the MRP printed on those goods is recorded against that batch

#### Scenario: Successive batches may differ in MRP

- **WHEN** a batch of a product is received bearing an MRP different from an earlier batch
- **THEN** both batches retain their own recorded MRP

#### Scenario: The product reports the most recent MRP

- **WHEN** a product's MRP is requested for display
- **THEN** the MRP of its most recently received batch is returned

#### Scenario: An estimated value is distinguishable from a printed one

- **WHEN** goods carry no printed MRP and an estimated retail value is recorded
- **THEN** the recorded value is identified as an estimate rather than a printed MRP

### Requirement: The system suggests a selling price but does not set it

For a product with no selling price, the system SHALL calculate a suggested price from its allocated per-unit cost and a resolved target margin, and SHALL present it for confirmation. The suggestion SHALL NOT become the selling price until a person accepts or overrides it, and SHALL never be applied to a product that already has a price.

The target margin SHALL resolve most-specific-first: a transient custom margin supplied for this one suggestion, otherwise the margin configured for the product's category, otherwise a global default margin. A transient custom margin SHALL NOT be persisted — the price it produces is what is stored.

#### Scenario: A suggestion is offered for an unpriced product

- **WHEN** an unpriced product's batch has an allocated per-unit cost
- **THEN** the system calculates a suggested selling price achieving the resolved target margin
- **AND** presents it for confirmation

#### Scenario: The category margin is used when no custom margin is supplied

- **WHEN** a suggestion is calculated for a product whose category has a configured margin, with no custom margin supplied
- **THEN** the suggestion uses the category's margin rather than the global default

#### Scenario: A custom margin overrides the category margin and is not stored

- **WHEN** a suggestion is calculated with a transient custom margin supplied
- **THEN** the suggestion uses the custom margin
- **AND** no custom margin is persisted against the product

#### Scenario: The global default applies when the category has no configured margin

- **WHEN** a suggestion is calculated for a product whose category has no configured margin and no custom margin is supplied
- **THEN** the suggestion uses the global default margin

#### Scenario: A suggestion does not become a price on its own

- **WHEN** a suggested selling price has been calculated but not accepted
- **THEN** the product remains unpriced
- **AND** is not sellable

#### Scenario: The suggestion can be overridden

- **WHEN** a person sets a selling price different from the suggestion
- **THEN** the product's selling price becomes the value they set

#### Scenario: Priced products receive no suggestion

- **WHEN** a batch is received for a product that already has a selling price
- **THEN** no suggested price is applied
- **AND** the existing selling price is unchanged

### Requirement: A product without a selling price cannot be sold

A product SHALL be allowed to exist without a selling price, because stock is routinely received before anyone has decided what it is worth. Such a product SHALL be identifiable as unpriced and SHALL NOT be sellable until a price is set. An absent price SHALL NOT be treated as a price of zero.

#### Scenario: A product may be created unpriced

- **WHEN** a product is created without a selling price
- **THEN** the product is stored successfully
- **AND** it is reported as unpriced

#### Scenario: An absent price is not zero

- **WHEN** the selling price of an unpriced product is requested
- **THEN** the system reports that no price is set
- **AND** does not return zero

#### Scenario: Setting a price makes the product sellable

- **WHEN** a selling price is set on an unpriced product
- **THEN** the product is no longer reported as unpriced
- **AND** it becomes sellable

### Requirement: Margin erosion flags a product for price review

Gross margin SHALL be calculated as (selling price − batch cost) ÷ selling price, expressed as a percentage. When stock is received at a cost that reduces a product's gross margin by 5 percentage points or more compared with its margin at the most recent prior batch cost, the system SHALL flag that product for price review. The threshold SHALL be configurable rather than fixed in code.

The system SHALL NOT change the selling price in response. Flagging surfaces the decision; it does not make it.

#### Scenario: Margin erosion raises a flag

- **WHEN** stock is received at a cost that reduces the product's gross margin by 5 percentage points or more against its margin at the most recent prior batch cost
- **THEN** the product is flagged for price review
- **AND** the product's selling price is unchanged

#### Scenario: Erosion below the threshold raises no flag

- **WHEN** stock is received at a cost that reduces gross margin by less than the configured threshold
- **THEN** the product is not flagged
- **AND** the product's selling price is unchanged

#### Scenario: A cheaper batch raises no flag

- **WHEN** stock is received at a cost lower than the most recent prior batch cost
- **THEN** the product is not flagged for price review

#### Scenario: The first batch of a product raises no flag

- **WHEN** the first batch of a product is received
- **THEN** the product is not flagged, there being no prior cost to compare against

#### Scenario: An unpriced product raises no flag

- **WHEN** stock is received for a product that has no selling price
- **THEN** the product is not flagged, no margin being computable

#### Scenario: Setting a price clears the flag

- **WHEN** a selling price is explicitly set on a flagged product
- **THEN** the price review flag is cleared

#### Scenario: The threshold is configurable

- **WHEN** the margin review threshold is configured to a value other than 5 percentage points
- **THEN** flagging uses the configured value

### Requirement: A flagged product remains sellable

A product flagged for price review SHALL continue to sell at its existing selling price until someone changes it. Flagging SHALL NOT block the sale, withhold the stock, or alter what the customer is charged, so that an unresolved pricing question never becomes a refusal at the counter.

#### Scenario: A flagged product sells at its existing price

- **WHEN** a product flagged for price review is sold
- **THEN** the sale completes at the product's existing selling price
- **AND** the flag remains set

### Requirement: Monetary values are stored exactly

All monetary values SHALL be stored as integer minor units (paise). No monetary value SHALL be stored or computed as a floating-point number, so that no rounding artefact can be introduced by storage.

#### Scenario: Monetary values round-trip exactly

- **WHEN** a monetary value is stored and then retrieved
- **THEN** the retrieved value equals the stored value exactly
