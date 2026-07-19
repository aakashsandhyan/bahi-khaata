## ADDED Requirements

### Requirement: A purchase is recorded as a lot

Stock is bought as a lot for a single sum rather than per product, so the purchase itself SHALL be recorded: supplier, date, the total amount paid, and any freight or transport cost incurred to receive it. Every batch SHALL belong to exactly one lot. The lot is what was actually paid and is the auditable figure; per-product costs are derived from it.

#### Scenario: A purchase creates a lot

- **WHEN** a purchase of stock is recorded
- **THEN** a lot is created recording the supplier, date, total amount paid, and freight cost
- **AND** every batch created from that purchase is linked to it

#### Scenario: Freight forms part of the amount allocated

- **WHEN** a lot is recorded with a freight cost
- **THEN** the amount allocated across its batches is the total paid plus freight

### Requirement: Lot cost is allocated across its products by relative retail value

The cost of a lot SHALL be allocated across the products it contains in proportion to each line's total MRP, so that products of different value carry proportionally different costs. Where a product's MRP is unknown, an estimated retail value SHALL be supplied and used in its place.

Allocation by unit count SHALL NOT be used for mixed lots, because it assigns the same cost to items of wildly different value and renders per-product margin meaningless.

#### Scenario: Cost is allocated in proportion to retail value

- **WHEN** a lot containing several products is allocated
- **THEN** each line receives a share of the lot amount in proportion to its quantity multiplied by its MRP

#### Scenario: An unknown MRP uses an estimate

- **WHEN** a line's product has no MRP
- **THEN** an estimated retail value is required for that line
- **AND** allocation uses that estimate in place of MRP

#### Scenario: Per-unit cost derives from the line allocation

- **WHEN** a line has been allocated its share of the lot amount
- **THEN** the batch's per-unit cost is that share divided by the sellable quantity received

### Requirement: Known per-unit costs are pinned and excluded from allocation

Where the actual cost of a product is known — because the supplier itemised it — a manager SHALL be able to pin that per-unit cost. Pinned lines SHALL take their stated cost, and their total SHALL be deducted from the lot amount before the remainder is allocated across the unpinned lines. Pinning SHALL NOT be a post-hoc adjustment of an allocated figure.

#### Scenario: Pinned lines are removed from the allocation pool

- **WHEN** a lot is allocated and one or more lines carry a pinned per-unit cost
- **THEN** those lines take their pinned cost
- **AND** the remaining lines share only the lot amount less the pinned total

#### Scenario: Pinning changes what the other lines receive

- **WHEN** a line's cost is pinned on a lot that was previously fully allocated
- **THEN** the costs allocated to the unpinned lines are recalculated against the reduced remainder

#### Scenario: Pinned costs exceeding the lot amount are refused

- **WHEN** the total of pinned line costs exceeds the lot amount
- **THEN** the system refuses the allocation
- **AND** reports the excess

#### Scenario: Every line pinned requires the total to reconcile

- **WHEN** every line of a lot carries a pinned cost
- **THEN** the allocation is accepted only if the pinned total equals the lot amount

### Requirement: Allocated costs reconcile exactly to the lot amount

The sum of all allocated line costs SHALL equal the lot amount exactly, in integer paise. Where proportional division leaves a remainder, that remainder SHALL be assigned by a defined rule rather than lost to rounding, so that the books never drift.

#### Scenario: Allocation sums to the lot amount

- **WHEN** a lot has been allocated across its lines
- **THEN** the sum of the allocated line costs equals the lot amount exactly

#### Scenario: A rounding remainder is assigned, not discarded

- **WHEN** proportional allocation leaves a remainder of one or more paise
- **THEN** the remainder is assigned to the line with the largest allocated share
- **AND** the total still equals the lot amount exactly

### Requirement: The basis of every cost is recorded alongside it

Each batch SHALL record how its cost price was determined — allocated from the lot, pinned from a known per-unit cost, or imported from a supplied cost list — and each lot SHALL record which allocation method produced its figures. A cost figure without its basis cannot be judged, and the basis cannot be reconstructed after the fact.

#### Scenario: An allocated cost records its basis

- **WHEN** a batch's cost is derived by allocating the lot amount
- **THEN** the batch records that its cost was allocated

#### Scenario: A pinned cost records its basis

- **WHEN** a batch's cost comes from a pinned per-unit cost
- **THEN** the batch records that its cost was pinned

#### Scenario: An imported cost records its basis

- **WHEN** a batch's cost comes from an imported cost list
- **THEN** the batch records that its cost was imported

#### Scenario: The lot records the method used

- **WHEN** a lot is allocated
- **THEN** the lot records which allocation method produced its figures

### Requirement: Changing the allocation method does not re-cost existing lots

A lot SHALL retain the costs produced by the method in force when it was allocated. Introducing or selecting a different allocation method SHALL NOT recalculate the costs of lots already allocated, because doing so would rewrite the margin history those costs support.

#### Scenario: Existing lots keep their original costs

- **WHEN** the allocation method is changed
- **THEN** the costs recorded against previously allocated lots are unchanged
- **AND** those lots continue to report the method that produced them

#### Scenario: New lots use the current method

- **WHEN** a lot is allocated after the method has changed
- **THEN** the new method produces its costs
- **AND** the lot records that method

### Requirement: Damaged units are excluded from allocation and their cost absorbed

Units received damaged or otherwise unsellable SHALL be recorded but excluded from the quantity across which cost is allocated. Their share SHALL be absorbed by the sellable units, raising the per-unit cost of what can actually be sold, because the full lot amount was paid regardless of how much of it earns.

#### Scenario: Damaged units raise the cost of sellable units

- **WHEN** a line records a quantity received and a smaller sellable quantity
- **THEN** the line's per-unit cost is its allocated share divided by the sellable quantity
- **AND** the lot amount remains fully allocated

#### Scenario: Damaged quantity is recorded

- **WHEN** units are received damaged
- **THEN** the quantity damaged is recorded against the batch
- **AND** those units do not count toward quantity on hand

### Requirement: Each delivery of a product is recorded as its own batch

Every arrival of a product SHALL create a batch recording its allocated cost price, quantity, and date, linked to both its product and its lot. Receiving the same product again SHALL create a new batch rather than altering an existing one, because the same product routinely arrives at different costs.

#### Scenario: Receiving stock creates a batch

- **WHEN** stock is received for a product as part of a lot
- **THEN** a batch is created recording its allocated cost price, quantity received, and date
- **AND** the batch is linked to that product and to its lot

#### Scenario: Repeat delivery does not alter earlier batches

- **WHEN** stock for a product that already has batches is received at a different cost
- **THEN** a new batch is created with the new cost
- **AND** the cost recorded on every existing batch is unchanged

#### Scenario: Stock may be received before a selling price is decided

- **WHEN** stock is received for a product that has no selling price
- **THEN** the batch is created recording its cost and quantity
- **AND** the product remains unpriced rather than being assigned a price derived from cost

### Requirement: The stock ledger is append-only

Every inventory movement SHALL append a row to the stock ledger. Ledger rows SHALL NOT be updated or deleted once written, and the database SHALL reject attempts to do so rather than relying on application code to prevent them.

#### Scenario: Movements append rows

- **WHEN** an inventory movement occurs
- **THEN** a new ledger row is appended recording the product, batch, signed quantity, movement type, and effective date

#### Scenario: Updating a ledger row is rejected at the database

- **WHEN** an update is attempted against an existing ledger row, including directly against the database
- **THEN** the database rejects the update
- **AND** the row is unchanged

#### Scenario: Deleting a ledger row is rejected at the database

- **WHEN** a delete is attempted against an existing ledger row, including directly against the database
- **THEN** the database rejects the delete
- **AND** the row remains present

### Requirement: Every movement records why it occurred

Each ledger row SHALL carry a movement type recording the reason for the movement — at minimum purchase receipt, sale, damage write-off, and stock adjustment. Quantity SHALL be signed, with direction carried by the sign rather than by a separate field, so that no row can record a direction contradicting its quantity.

#### Scenario: A receipt is recorded as a positive movement

- **WHEN** stock is received
- **THEN** a ledger row is appended with a positive quantity and the purchase receipt movement type

#### Scenario: A sale is recorded as a negative movement

- **WHEN** stock is consumed by a sale
- **THEN** a ledger row is appended with a negative quantity and the sale movement type

#### Scenario: An adjustment may go in either direction

- **WHEN** a stock adjustment is recorded
- **THEN** the ledger row carries the stock adjustment movement type
- **AND** its quantity may be either positive or negative

#### Scenario: Movement type is distinguishable when reconciling

- **WHEN** ledger rows for a product are examined
- **THEN** movements arising from sales can be distinguished from adjustments and write-offs

### Requirement: A lot is editable until stock from it has been consumed

A lot and its batches SHALL be editable while no stock from that lot has been consumed, so that a data-entry error can simply be corrected. Once any batch of the lot has been consumed, the lot and all its batches SHALL become frozen, because the allocated costs have by then been used to record cost of goods sold and changing them would rewrite margin history. Corrections after freezing SHALL be made as adjustment movements rather than edits.

#### Scenario: An unconsumed lot can be corrected

- **WHEN** a lot with no consumed stock is edited
- **THEN** the edit is accepted
- **AND** its batch costs are reallocated from the corrected amount

#### Scenario: Consumption freezes the whole lot

- **WHEN** stock from any batch of a lot has been consumed
- **THEN** further edits to that lot or to any of its batches are refused

#### Scenario: A frozen lot is corrected by adjustment

- **WHEN** a correction is needed for a frozen lot
- **THEN** it is recorded as an adjustment movement
- **AND** the lot's recorded amount and allocated costs are unchanged

### Requirement: Stock on hand is derived from the ledger

Quantity on hand SHALL be derived from the ledger rather than held as an independently mutable counter, so that the ledger remains the single source of truth and no stored total can silently disagree with the movements that produced it.

#### Scenario: On-hand reflects appended movements

- **WHEN** movements are appended for a product
- **THEN** the reported quantity on hand equals the net of those movements

#### Scenario: On-hand is reported per batch

- **WHEN** quantity on hand is requested for a product with multiple batches
- **THEN** the system reports the remaining quantity attributable to each batch

### Requirement: Stock is consumed oldest batch first

When stock is consumed, the system SHALL consume it from the oldest batch with quantity remaining, and SHALL move to the next oldest once a batch is exhausted. Cost of goods sold SHALL be attributed at the cost of the batch actually consumed.

#### Scenario: Consumption draws from the oldest batch

- **WHEN** stock is consumed for a product holding multiple batches
- **THEN** the quantity is drawn from the batch with the earliest arrival date that has quantity remaining

#### Scenario: Consumption spans batches when the oldest is exhausted

- **WHEN** the quantity consumed exceeds what remains in the oldest batch
- **THEN** the remainder is drawn from the next oldest batch with quantity remaining
- **AND** the movement records how much was drawn from each batch

#### Scenario: Cost of goods sold uses the consumed batch cost

- **WHEN** stock is consumed from a batch
- **THEN** the cost of goods sold recorded for that consumption is the cost price of that batch
- **AND** consumption spanning two batches at different costs records each portion at its own batch cost

### Requirement: Backdated movements recalculate forward

A movement recorded with an effective date earlier than existing movements SHALL be appended, and derived figures from that date onward SHALL be recalculated. Existing ledger rows SHALL NOT be rewritten to accommodate it.

#### Scenario: A late-logged delivery is accepted

- **WHEN** a movement is recorded with an effective date earlier than the most recent existing movement
- **THEN** the movement is appended to the ledger
- **AND** no existing ledger row is modified

#### Scenario: Derived figures reflect the backdated movement

- **WHEN** a backdated movement has been appended
- **THEN** quantity on hand and valuation reported for dates at or after its effective date account for it

### Requirement: Stock valuation is reconstructible for any past date

The system SHALL report quantity on hand and stock valuation as at any given date, computed from the ledger, so that historical positions can be reconstructed and audited rather than inferred.

#### Scenario: Valuation as at a past date

- **WHEN** stock valuation is requested as at a past date
- **THEN** the system returns the valuation implied by movements effective on or before that date
- **AND** movements effective after that date are excluded
