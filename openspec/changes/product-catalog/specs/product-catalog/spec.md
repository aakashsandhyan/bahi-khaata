## ADDED Requirements

### Requirement: Name-filtered product listing

The catalogue SHALL list products whose name contains a given text, case-insensitively, ordered by
name. The listing SHALL be paged so it is not capped at a fixed small number, and an empty filter
SHALL list products from the start of the catalogue rather than nothing.

#### Scenario: Filtering by a name fragment
- **WHEN** the catalogue is asked for products matching "cooker"
- **THEN** every product whose name contains "cooker" (any case) is returned, ordered by name

#### Scenario: Empty filter lists the catalogue
- **WHEN** the catalogue is asked with no name filter
- **THEN** products are listed from the start, ordered by name, paged

#### Scenario: More results than one page
- **WHEN** more products match than fit on one page
- **THEN** the caller can request further pages and receive the next products in name order

### Requirement: Found versus on-paper status

Every listed product SHALL carry a status of **found** or **on-paper**, derived at read time, never
stored. A product is **found** when it has at least one counted batch OR at least one barcode whose
origin is not the marketplace reference (i.e. a manufacturer, returns-label, or internal code). A
product is **on-paper** when it has neither — it exists only from the manifest, by its marketplace
reference, with nothing counted and no physical code.

#### Scenario: Manifest-only product is on-paper
- **WHEN** a product exists with only its marketplace reference, no batch and no physical code
- **THEN** its status is on-paper

#### Scenario: A counted product is found
- **WHEN** a product has at least one counted batch
- **THEN** its status is found, whether or not a physical code was ever mapped onto it

#### Scenario: A tagged-but-not-counted product is found
- **WHEN** a product has a manufacturer, returns-label, or internal code mapped but no batch yet
- **THEN** its status is found

#### Scenario: The marketplace reference alone does not count as found
- **WHEN** a product's only code is its marketplace reference
- **THEN** it is not treated as found on the strength of that code

### Requirement: On-paper products surfaced first

The catalogue SHALL let a person see the on-paper products — the goods a delivery still owes but
nobody has found — without having to scan the boxes, and SHALL present them by default. The caller
SHALL be able to choose between on-paper only, found only, and all products.

#### Scenario: Default view shows the gaps
- **WHEN** the catalogue is opened without a chosen status
- **THEN** on-paper products are shown first, as the default

#### Scenario: Choosing found only
- **WHEN** the caller asks for found products only
- **THEN** only products with a batch or a physical code are listed

#### Scenario: Choosing all
- **WHEN** the caller asks for all products
- **THEN** both found and on-paper products are listed, ordered by name

### Requirement: Filtering by department

The catalogue SHALL let a person restrict the list to one department (category), and this filter
SHALL combine with the name filter and the found/on-paper filter — each filter that is set narrows
the list further. Choosing every department SHALL impose no category restriction.

#### Scenario: One department
- **WHEN** a department is chosen
- **THEN** only products in that department are listed

#### Scenario: Department combines with status
- **WHEN** a department is chosen together with the found or on-paper filter
- **THEN** only products in that department with that status are listed

#### Scenario: Every department
- **WHEN** no department is chosen
- **THEN** products across all departments are listed, subject to the other filters

### Requirement: Opening a product to its detail

Selecting a product from the catalogue SHALL open a detail showing the product's name and category,
every state its stock is held in with quantities, and the codes mapped onto it. The detail SHALL
reuse the existing product-states view rather than compute a new one.

#### Scenario: Viewing a found product
- **WHEN** a found product is selected
- **THEN** its name, category, per-condition stock quantities, and mapped codes are shown

#### Scenario: Viewing an on-paper product
- **WHEN** an on-paper product is selected
- **THEN** its name and category are shown, with no stock and only its marketplace reference

### Requirement: Acting on a product from the catalogue

From a product's detail the catalogue SHALL offer the actions that do not require a box context — 
setting a selling price and mapping a code onto the product — reusing the existing operations for
each, subject to their existing rules.

#### Scenario: Setting a price
- **WHEN** a price is set on a product from its detail
- **THEN** the existing set-price operation runs, with its existing rules (a real positive amount,
  within the recorded MRP), and the product's priced status updates

#### Scenario: Mapping a code
- **WHEN** a code is mapped onto a product from its detail
- **THEN** the existing code-mapping operation runs, and the product becomes found if it was on-paper

### Requirement: Counting is a hand-off, not performed in the catalogue

Counting SHALL NOT be performed from the catalogue, because counting belongs to a box and an open
lot. The catalogue SHALL act as the picker: it selects a product and signals intent to count, so the
separate product-centric counting flow can attach to it. The catalogue itself SHALL NOT record any
count.

#### Scenario: Count from the catalogue hands off
- **WHEN** count is chosen on a product in the catalogue
- **THEN** the product is selected and counting intent is signalled, and no count is recorded by the
  catalogue

#### Scenario: The catalogue is a reusable picker
- **WHEN** another flow needs a product chosen by name or status
- **THEN** it can select through the same catalogue rather than building its own finder
