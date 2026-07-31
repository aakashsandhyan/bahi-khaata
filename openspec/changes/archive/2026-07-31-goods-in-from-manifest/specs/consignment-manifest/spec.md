## ADDED Requirements

### Requirement: A manifest records an expectation and puts nothing on hand

Importing a supplier's manifest SHALL record what the supplier claims is coming and SHALL NOT record any stock movement. Stock SHALL enter the ledger only when someone has opened a box and counted what is inside.

A manifest is an expectation. Recording it as fact would state quantities nobody has verified, in a trade where manifests and deliveries routinely disagree.

#### Scenario: Importing a manifest leaves nothing on hand

- **WHEN** a manifest is imported
- **THEN** the lot and its expected lines are recorded
- **AND** no stock ledger entry is written
- **AND** stock on hand for every product in it is unchanged

#### Scenario: An expected line records what was claimed

- **WHEN** a manifest line states a quantity for a product
- **THEN** that quantity is recorded as expected
- **AND** it remains readable after counting, so what was claimed can be compared with what arrived

### Requirement: Products are created at import, stock is not

Importing a manifest SHALL create a catalogue entry for each product it names, using the supplier's code as the product's barcode. Creating the product SHALL NOT imply that any of it is held.

The catalogue entry is what the unpacking screen matches a scan against; without it, staff would have to identify goods by typing.

#### Scenario: A named product becomes a catalogue entry

- **WHEN** a manifest names a product not already in the catalogue
- **THEN** a product is created bearing the supplier's code as its barcode
- **AND** it has no stock on hand

#### Scenario: An already-known product is matched, not duplicated

- **WHEN** a manifest names a product whose code already resolves to an existing product
- **THEN** that existing product is used
- **AND** no second product or barcode is created

#### Scenario: A product that never arrives remains a catalogue entry

- **WHEN** an expected line is never counted because the goods did not arrive
- **THEN** the product remains in the catalogue with no stock
- **AND** it is not treated as an error

### Requirement: Every expected line names the box it should arrive in

An expected line SHALL record the tracking number of the physical box carrying it. A box MAY carry one or many lines.

The tracking number is already printed on the carton and already present in the manifest. Without it there is no way to answer which cartons remain unopened.

#### Scenario: A box groups its expected lines

- **WHEN** a tracking number is looked up
- **THEN** every expected line due to arrive in that box is returned

#### Scenario: A box holding a single line is not a special case

- **WHEN** a box carries exactly one expected line
- **THEN** it behaves the same as a box carrying many

### Requirement: Supplier category codes are mapped, and an unknown one is refused

A manifest's own product-line codes SHALL be mapped to categories on import. An unrecognised code SHALL cause the import to fail, reporting the code, rather than being guessed at or assigned to a default.

A wrongly categorised product carries the wrong margin, and a default category would hide that behind a plausible-looking result.

#### Scenario: A known supplier code maps to a category

- **WHEN** a manifest line carries a supplier product-line code that is mapped
- **THEN** the product is created in the corresponding category

#### Scenario: An unknown supplier code fails the import

- **WHEN** a manifest line carries a product-line code with no mapping
- **THEN** the import fails reporting that code
- **AND** nothing from the manifest is recorded

### Requirement: A manifest states either supplier cost or market price, and the two are not conflated

A manifest SHALL be imported under one of two pricing schemes. Where it states a supplier cost, that cost SHALL be used only as the weight for apportioning what was paid. Where it states a marketplace selling price, that price SHALL additionally be recorded against the product as an observed online price.

A supplier's cost SHALL NOT be recorded as an online price. They are different figures about different things, and storing one as the other makes the catalogue lie about what goods fetch.

#### Scenario: A market price is recorded from an off-market manifest

- **WHEN** a manifest line states the price the goods sold for on a marketplace
- **THEN** that price is recorded against the product together with the marketplace and the date observed

#### Scenario: A cost-plus manifest records no market price

- **WHEN** a manifest states a supplier cost rather than a marketplace price
- **THEN** the product's online price remains unset
- **AND** the supplier cost is used only as an apportioning weight

#### Scenario: Repeated rows for one product average their market price

- **WHEN** a manifest carries several rows for the same product quoting different marketplace prices
- **THEN** the recorded online price is the average of those prices weighted by quantity
- **AND** it does not depend on the order the rows appear in

#### Scenario: Prices from two marketplaces are refused

- **WHEN** rows for one product quote prices from different marketplaces
- **THEN** the import fails reporting the conflict
- **AND** no averaged figure is recorded

### Requirement: Amounts are imported without loss of precision

Monetary amounts SHALL be read from the manifest at full stated precision. A rounded intermediate value SHALL NOT be used where an exact one is available.

A rounded export of the first real consignment shifted a category total by ₹52 and took an afternoon to trace.

#### Scenario: Sub-paise values are not rounded before totalling

- **WHEN** a manifest states amounts carrying fractions below one paisa
- **THEN** totals are computed from the stated values
- **AND** rounding occurs once, at the point a value is stored

#### Scenario: Thousands separators do not corrupt an amount

- **WHEN** an amount is written with thousands separators
- **THEN** it is read as the number it denotes
