## ADDED Requirements

### Requirement: In-hand count captured at pricing

Pricing SHALL capture an **in-hand** quantity — the true physical count taken as the goods are handled to price them — and reconcile stock to it. The manifest's **expected** quantity SHALL be shown for reference only and SHALL NOT be edited at pricing. Unpacking's count is treated as a rough first pass; pricing is the count of record.

The **first time** a product is priced (it has no selling price yet), the in-hand quantity SHALL be the total on hand and SHALL **overwrite** the product's on-hand — whether higher or lower than what unpacking counted. The field SHALL default to the counted quantity, so confirming a correct count is a single action.

A **later** pricing of an already-priced product SHALL treat the quantity as **additional pieces found** and SHALL **add** it to on-hand. It SHALL NOT reduce on-hand, and SHALL default to zero — so a re-price that only corrects the price or MRP moves no stock.

#### Scenario: First pricing overwrites the count upward

- **WHEN** a product not yet priced, counted at 7, is priced with an in-hand quantity of 8
- **THEN** its on-hand becomes 8

#### Scenario: First pricing overwrites the count downward

- **WHEN** a product not yet priced, counted at 7, is priced with an in-hand quantity of 5
- **THEN** its on-hand becomes 5

#### Scenario: Re-pricing adds the pieces found later

- **WHEN** an already-priced product with on-hand 4 is priced again with an added quantity of 3
- **THEN** its on-hand becomes 7

#### Scenario: Re-pricing to fix a figure moves no stock

- **WHEN** an already-priced product is priced again with an added quantity of 0 and a corrected MRP
- **THEN** its MRP is updated and its on-hand is unchanged

#### Scenario: Expected is shown, the in-hand defaults to the count

- **WHEN** a product with an expected quantity of 11, counted at 7, is opened for pricing
- **THEN** the expected 11 is shown for reference and the in-hand quantity defaults to 7, not 11

### Requirement: Quantity entry is clearable

A quantity field at pricing SHALL let the operator delete the current value and type a new one; it SHALL clamp to a whole number no smaller than its minimum only when focus leaves the field, not on each keystroke.

#### Scenario: The field can be emptied and retyped

- **WHEN** the operator deletes every digit in a quantity field and types a new number
- **THEN** the field shows what was typed while typing and holds the new number after focus leaves it
