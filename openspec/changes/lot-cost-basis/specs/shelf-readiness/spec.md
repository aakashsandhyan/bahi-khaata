## MODIFIED Requirements

### Requirement: A margin price needs the batch's cost, which is known at receipt

Setting a margin-based selling price SHALL require the batch's cost to be known. A batch costed from its pinned cost — the manifest rate, a flat cost basis, or a cost basis whose anchor is already known — is known as soon as it is costed, so its product MAY be priced without the lot being closed. A batch whose cost basis anchors to an MRP or ASP that is not yet known is uncosted until that anchor is recorded, and cannot be priced by margin until then. Only an uncosted batch — a surplus no manifest line named, or an anchor not yet known — lacks a cost to compute a margin against.

#### Scenario: A costed product is priceable at receipt

- **WHEN** a product's batch is costed from its pinned manifest cost or a flat cost basis
- **THEN** a margin price may be set for it without the lot being closed

#### Scenario: An anchored product is priceable once its anchor is known

- **WHEN** a product's batch anchors its cost to MRP and the MRP has just been recorded
- **THEN** the batch becomes costed and a margin price may then be set for it

#### Scenario: An uncosted batch has no cost to price a margin against

- **WHEN** a product's only batch is uncosted — a surplus with no stated cost, or a cost-basis anchor not yet known
- **THEN** a margin cannot be computed for it until a cost is determined
