## ADDED Requirements

### Requirement: Products are classified into GST rate-groups

The system SHALL provide a controlled `sub_category` vocabulary — each entry a code, a name, and a
parent `category` — and each product MAY carry one `sub_category`. The `sub_category` is the
classification a GST rate attaches to; free-text classification SHALL NOT be permitted.

#### Scenario: A sub-category belongs to a category
- **WHEN** a sub-category is created
- **THEN** it has a unique code, a name, and a parent category, and a product may be assigned to it

#### Scenario: A product's sub-category is controlled
- **WHEN** a product is assigned a sub-category
- **THEN** the value must be an existing sub-category code, not free text

### Requirement: A GST rate is effective-dated per sub-category

The system SHALL hold GST rates in an effective-dated table keyed by `sub_category`, where each rate
is a decimal percent with an `effective_from`, an `effective_to`, and an `active` flag. Exactly one
rate per sub-category SHALL be active at a time. Changing a rate SHALL close the current active rate
and open a new active one, never editing history.

#### Scenario: Only one active rate per sub-category
- **WHEN** a sub-category already has an active rate and a new rate is set for it
- **THEN** the previous rate is closed (marked inactive with an end date) and the new rate becomes
  the single active rate for that sub-category

#### Scenario: Rate percent is exact
- **WHEN** a rate is stored
- **THEN** it is kept as a decimal percent so non-integer slabs (e.g. 0.25%, 3%) are represented
  exactly, not rounded

### Requirement: The GST rate for a sale is resolved from the sub-category, with a default

The system SHALL resolve the GST rate for a product from its sub-category's active rate, falling
back to a global default rate when the product has no sub-category. The global default SHALL be an
editable setting, defaulting to 18%.

#### Scenario: Rate comes from the sub-category
- **WHEN** a classified product is priced or sold
- **THEN** its GST rate is the active rate of its sub-category

#### Scenario: Unclassified falls back to the default
- **WHEN** a product without a sub-category is resolved
- **THEN** the global default rate (18% unless changed) is used

### Requirement: GST is extracted inclusively into CGST and SGST

Because the selling price is GST-inclusive, the system SHALL extract the tax from the price rather
than add it on top: the taxable value plus the tax equal the selling price. For a line at rate `r`,
the line tax SHALL be `price × r / (100 + r)`. The invoice tax SHALL be the sum of line taxes rounded
to the nearest rupee (CGST Act §170), split equally into CGST and SGST. The invoice total SHALL equal
the sum of the selling prices — no amount is added.

#### Scenario: Tax is extracted, not added
- **WHEN** an item priced ₹510 at 18% is sold
- **THEN** the line total remains ₹510, the tax is ₹510 × 18 / 118 ≈ ₹77.80, and the taxable value is
  the remainder — the total is never inflated above the selling price

#### Scenario: CGST and SGST split
- **WHEN** the invoice tax is computed for a same-state B2C sale
- **THEN** it is split equally into CGST and SGST (never IGST), and rounded to the nearest rupee at
  the invoice total

### Requirement: The resolved rate is frozen onto the sale

The system SHALL snapshot the resolved GST rate and the derived tax onto the cart line at ring time
and onto the sale line at completion, so a later rate change never alters the tax on a completed
sale.

#### Scenario: A later rate change does not move a past sale's tax
- **WHEN** a sub-category's rate is changed after a sale that included one of its products
- **THEN** the earlier sale's tax, CGST, SGST, and taxable value stay exactly as they were at
  completion

### Requirement: Existing products are classified best-effort, unresolved left to the till

The system SHALL classify existing products into sub-categories by a one-time best-effort pass,
assigning a sub-category only when confident and leaving low-confidence products unclassified rather
than guessing. An unclassified product SHALL be prompted for its sub-category once at the point of
sale before its line can be rung, and the choice SHALL persist on the product.

#### Scenario: Confident classification is applied
- **WHEN** the backfill runs over the catalogue
- **THEN** products it can classify with confidence receive a sub-category, and products it cannot
  are left unclassified for later resolution

#### Scenario: An unclassified item is resolved at the till
- **WHEN** an unclassified product is scanned into a sale
- **THEN** the operator is prompted once to assign its sub-category, which persists on the product,
  and the line then rings at the resolved rate

### Requirement: GST rates and sub-categories are managed by an admin

The system SHALL let an admin view and edit the sub-category vocabulary and the per-sub-category GST
rates, without a code change; a rate edit follows the close-and-open rule.

#### Scenario: An admin corrects a rate before go-live
- **WHEN** an admin sets a new percent for a sub-category
- **THEN** the previous rate is closed and the new one becomes active, and subsequent sales use it
