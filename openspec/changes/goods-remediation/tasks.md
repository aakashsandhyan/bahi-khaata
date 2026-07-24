# Tasks — goods-remediation

## 1. Schema & migrations

- [x] 1.1 Migration: widen the `batch` condition CHECK to include `NEEDS_WORK`, via a non-transactional table rebuild (as V17/V20 did for `DAMAGED`/`UNUSABLE`). — V25
- [x] 1.2 Same or follow-on migration: add nullable `issue_type` column to `batch`, with a CHECK that it is non-null iff `condition = 'NEEDS_WORK'`. — V25
- [x] 1.3 Replace the batch uniqueness with a `COALESCE(issue_type,'')` unique index over `(lot_id, product_id, condition, COALESCE(issue_type,''))`, so one `GOOD`/`DAMAGED`/`UNUSABLE` batch per product still holds while needs-work splits by issue type. — V25
- [x] 1.4 Migration: create `issue_type(code, label)` and `category_issue_type(category_code, issue_type_code)` tables. — V24
- [x] 1.5 Migration: seed the locked grid — issue types and their category mappings. — V24

## 2. Domain model

- [x] 2.1 Add `NEEDS_WORK` to the `StockCondition` enum in contracts; handle it in the condition branches (addToBatch off-ledger, LotClosing divisor includes it).
- [x] 2.2 Add `issue_type` to the `Batch` entity (nullable), with a getter and its place in the `counted(...)` factory.
- [x] 2.3 Add repositories/queries for issue types by category (`IssueType` + `IssueTypeRepository.findForCategory`).

## 3. Cost at close

- [x] 3.1 Include `NEEDS_WORK` quantities in the lot-close cost divisor alongside `GOOD` and `DAMAGED` — already so, via the existing `!= UNUSABLE` filter; held by a test.
- [x] 3.2 Confirm the sellability gate excludes `NEEDS_WORK` — off-ledger, so it is not on hand and cannot be sold.

## 4. Remediation service

- [x] 4.1 Create `GoodsRemediation` with `changeState(...)`, reusing the batch-add and ledger helpers.
- [x] 4.2 Refuse a state change against a closed lot.
- [x] 4.3 Refuse a quantity larger than the source batch holds; nothing changes on refusal.
- [x] 4.4 Ledger rules: negative adjustment out of a stock-bearing state, receipt into one, nothing for an off-ledger side — needs-work→Ready raises on-hand, Ready→scrap lowers it, Seconds↔Ready holds it.
- [x] 4.5 Inherit the product's MRP onto the target batch.
- [x] 4.6 Extend counting so an item can be counted straight into `NEEDS_WORK` with an issue type.

## 5. API

- [x] 5.1 Endpoint: issue types available for a category (`GET /api/remediation/issue-types`).
- [x] 5.2 Endpoint: a product's state breakdown (`GET /api/remediation/products/{id}/states`).
- [x] 5.3 Endpoint: `changeState` (`POST /api/remediation/change-state`).
- [x] 5.4 Endpoint: the prep backlog (`GET /api/remediation/backlog`).

## 6. Web UI

- [x] 6.1 Unpacking count pane: "Needs work" outcome + category-scoped issue-type picker, then count with the issue type.
- [x] 6.2 Change-state on the Prep screen: open a product from the backlog → its state breakdown → move units between states.
- [x] 6.3 Prep backlog view: needs-work grouped by kind of work, in the dashboard list style.

## 7. Tests

- [x] 7.1 A needs-work unit is not sellable (off-ledger); moving it to Ready puts it on hand.
- [x] 7.2 A needs-work unit carries the same unit cost as a `GOOD` one from the same lot at close.
- [x] 7.3 On-hand: needs-work→Ready raises it, Ready→scrap lowers it, Seconds↔Ready holds it; ledger append-only.
- [x] 7.4 Over-move is refused and nothing changes; a closed-lot state change is refused.
- [x] 7.5 Two needs-work issue types coexist for one product (COALESCE unique index holds).
- [x] 7.6 Issue-type menu is category-scoped: FASHION offers Dry-clean not Rebuild; KITCHEN the reverse.
- [x] 7.7 Full backend suite green (311 tests); ArchUnit boundaries intact.
