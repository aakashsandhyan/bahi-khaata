## ADDED Requirements

### Requirement: Filtering by lot

The catalogue SHALL let a person restrict the list to one lot (one delivery), combining with the
name, status, and department filters. When a lot is chosen the listing SHALL show only products with
an expected line in that lot, and each row's expected and counted units SHALL be summed over that lot
alone rather than across every delivery; found and on-paper SHALL reflect that lot — found meaning a
unit has been counted in it. With no lot chosen the catalogue SHALL span all deliveries as before.

The lot filter is what scopes counting: a product can sit in several open lots, and choosing the lot
says which delivery's copy is being worked before the product is opened to count.

#### Scenario: Scoping the catalogue to one delivery
- **WHEN** a lot is chosen in the catalogue
- **THEN** only products expected in that lot are listed, and each row's expected/counted units are
  the totals for that lot

#### Scenario: The same product in two lots
- **WHEN** a product is expected in two open lots and one of them is chosen
- **THEN** the row shows that lot's expected and counted units, not the sum of both

#### Scenario: No lot chosen spans everything
- **WHEN** no lot is chosen
- **THEN** the catalogue lists products across all deliveries with units summed across every lot, as
  before
