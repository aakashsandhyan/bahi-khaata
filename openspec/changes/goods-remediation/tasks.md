# Tasks — goods-remediation

## 1. Schema & migrations

- [ ] 1.1 Migration: widen the `batch` condition CHECK to include `NEEDS_WORK`, via a non-transactional table rebuild (as V17/V20 did for `DAMAGED`/`UNUSABLE`).
- [ ] 1.2 Same or follow-on migration: add nullable `issue_type` column to `batch`, with a CHECK that it is non-null iff `condition = 'NEEDS_WORK'`.
- [ ] 1.3 Replace the batch uniqueness with a `COALESCE(issue_type,'')` unique index over `(lot_id, product_id, condition, COALESCE(issue_type,''))`, so one `GOOD`/`DAMAGED`/`UNUSABLE` batch per product still holds while needs-work splits by issue type.
- [ ] 1.4 Migration: create `issue_type(code, label)` and `category_issue_type(category_code, issue_type_code)` tables.
- [ ] 1.5 Migration: seed the locked grid — issue types (Clean, Repair, Rebuild/parts, Test, Repack, Dry-clean, Wash, Mend/stitch, Iron, Polish, Sole/heel repair) and their category mappings (KITCHEN, WIRELESS, FASHION, FOOTWEAR, HOME_ESSENTIALS, PERSONAL_CARE, GARDEN).

## 2. Domain model

- [ ] 2.1 Add `NEEDS_WORK` to the `StockCondition` enum in contracts; find every exhaustive switch/branch on `StockCondition` and handle the new value deliberately.
- [ ] 2.2 Add `issue_type` to the `Batch` entity (nullable), with a getter and its place in the `counted(...)` factory.
- [ ] 2.3 Add repositories/queries for issue types by category (`issueTypesFor(categoryCode)`).

## 3. Cost at close

- [ ] 3.1 Include `NEEDS_WORK` quantities in the lot-close cost divisor alongside `GOOD` and `DAMAGED` (they sell at full price), leaving `UNUSABLE` absorbed.
- [ ] 3.2 Confirm the sellability gate excludes `NEEDS_WORK` (only `GOOD`, and `DAMAGED` at its own price, are sellable).

## 4. Remediation service

- [ ] 4.1 Create `GoodsRemediation` with `changeState(product, lot, from, to, issueType?, quantity, at)`, reusing the batch-add and ledger helpers.
- [ ] 4.2 Refuse a state change against a closed lot.
- [ ] 4.3 Refuse a quantity larger than the source batch holds; make nothing change on refusal.
- [ ] 4.4 Ledger rules: negative adjustment out of a stock-bearing state, receipt/adjustment into one, nothing for an off-ledger side (`UNUSABLE`, `NEEDS_WORK`) — so needs-work→Ready raises on-hand, Ready→scrap lowers it, Seconds↔Ready holds it.
- [ ] 4.5 Inherit the product's MRP onto the target batch (reuse the MRP-inheritance path).
- [ ] 4.6 Extend counting so an item can be counted straight into `NEEDS_WORK` with an issue type.

## 5. API

- [ ] 5.1 Endpoint: issue types available for a product (by its category).
- [ ] 5.2 Endpoint: a product's state breakdown (counts per state, needs-work split by issue type).
- [ ] 5.3 Endpoint: `changeState` — move N units between two states for a product+lot.
- [ ] 5.4 Endpoint/read: the prep backlog — needs-work grouped by issue type (and category).

## 6. Web UI

- [ ] 6.1 Unpacking: add a "Needs work" outcome to the condition step, then a category-scoped issue-type picker, then count.
- [ ] 6.2 Change-state screen reached **by product**: scan (camera resolve) or search (reuse the pricing category-filtered list) → show state breakdown → move units between states.
- [ ] 6.3 Prep backlog view: needs-work grouped by issue type, in the dashboard list style.

## 7. Tests

- [ ] 7.1 A needs-work unit is not sellable; moving it to Ready makes it sellable.
- [ ] 7.2 A needs-work unit carries the same unit cost as a `GOOD` one from the same lot at close.
- [ ] 7.3 On-hand: needs-work→Ready raises it, Ready→scrap lowers it, Seconds→Ready holds it; no existing ledger entry is altered.
- [ ] 7.4 Over-move is refused and nothing changes; a closed-lot state change is refused.
- [ ] 7.5 A second `GOOD` batch is still refused (COALESCE unique index holds), while two needs-work issue types coexist for one product.
- [ ] 7.6 Issue-type menu is category-scoped: FASHION offers Dry-clean not Rebuild; KITCHEN the reverse.
- [ ] 7.7 Full backend suite green; ArchUnit boundaries intact.
