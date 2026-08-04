## ADDED Requirements

### Requirement: Selling decrements stock through the ledger, and may go negative

When a sale is completed, the system SHALL write `SALE` ledger movements that decrement the sold products' stock, drawn FIFO across each product's batches and capturing the cost of goods sold at each batch's cost. Selling SHALL NOT be blocked by the counted on-hand — the counter is the source of truth — so on-hand MAY go negative, and that negative stands as the true record that more was sold than the count showed.

#### Scenario: A sale decrements stock FIFO
- **WHEN** a sale of several units of a product is completed and the product has stock in more than one batch
- **THEN** `SALE` movements are written consuming the oldest batch first, then the next, each carrying that batch's cost as the cost of goods sold
- **AND** the product's on-hand drops by the quantity sold

#### Scenario: Selling more than the count shows drives on-hand negative
- **WHEN** a sale's quantity exceeds the product's counted on-hand
- **THEN** the sale still completes, the extra units are recorded against the newest batch, and that batch's on-hand goes negative
- **AND** the negative on-hand remains as the record that a recount is needed
