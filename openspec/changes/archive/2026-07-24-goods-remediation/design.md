## Context

The stock model has three conditions — `GOOD`, `DAMAGED`, `UNUSABLE` — set at counting and never changed after. Reality on the floor is richer: goods arrive that work but need cleaning, are sound but missing a part, are perfect but need pressing. Staff already rescue such items by hand (an induction wiped down, a kettle rebuilt from another's base) but the books cannot say so — there is no not-yet-ready state, and no way to move a unit's condition once recorded. The proposal adds both. All seven lots of the one real consignment are currently **open**, which removes the hardest constraint (settled costs) for now.

Two settled facts shape the design:
- **`Category` is already a table, not an enum** — chosen so category-specific behaviour can be added without code. The per-category issue menu is exactly that behaviour, so it must be data-driven too.
- **The stock ledger is append-only and immutable by trigger.** Every condition change must be expressed as new entries, never edits — the same discipline the undo-count and MRP paths already follow.

## Goals / Non-Goals

**Goals:**
- A needs-work state that is full-cost and full-price-eventually, never confused with cheaper seconds.
- A per-product-category menu of issue types, data-driven.
- One operation to move units between any two states, recorded append-only.
- A prep backlog, and an entry point that survives the carton being discarded.

**Non-Goals:**
- Reclassifying goods after their lot is closed (would disturb settled costs and set prices). Refused for now; revisited when a closed lot first needs it.
- A separate selling price for a rescued item — once Ready it sells at the ordinary price; once Seconds it uses the existing damaged-price path.
- An admin UI to edit the issue-type grid. Seeded now; editing is later work.
- Recording more than one issue per needs-work unit at once.

## Decisions

### 1. Needs-work is a fourth condition — cost-bearing, but off the ledger until it is made ready

`NEEDS_WORK` joins the `StockCondition` set. Its batches carry ordinary lot cost (they will sell at full price, so they belong in the cost divisor at close alongside `GOOD` and `DAMAGED`). But they are **not written to the stock ledger** and so do not count toward on-hand, because on-hand means goods a customer could be sold, and needs-work goods cannot be until prepared.

This keeps on-hand meaning what it means today — `GOOD` + `DAMAGED`, the stock-bearing states — and leaves `UNUSABLE` and `NEEDS_WORK` both off the ledger, differing only in cost (absorbed vs full). Moving needs-work into `GOOD` then writes a *receipt* (on-hand rises), exactly as rescuing scrap does.

**Rejected: put needs-work on the ledger** so a move to `GOOD` is a net-zero adjustment. It reads cleanly but redefines on-hand to include goods that cannot be sold, which misleads every stock view and the sellability question the shop actually asks.

### 2. The issue type is a nullable batch attribute, and it splits the batch key

A needs-work batch carries an `issue_type`. Because one product may hold units needing *different* work (some to clean, some to repair), the batch uniqueness widens from `(lot, product, condition)` to include the issue type. `issue_type` is null for the other three conditions and required for `NEEDS_WORK`; the unique index coalesces null to a blank so the existing "one batch per condition" guarantee is unchanged for `GOOD`/`DAMAGED`/`UNUSABLE`.

**Rejected: a separate remediation table** outside `batch`. It would duplicate cost, MRP, and quantity handling that batches already do, and force the close-time cost divisor to sum two sources. Keeping needs-work as a batch lets cost, MRP inheritance, and the transition operation reuse one path.

### 3. Issue types are a master list mapped to categories, seeded from the locked grid

Two tables: `issue_type(code, label)` and `category_issue_type(category_code, issue_type_code)`. "Clean" is one issue type mapped to several categories; "Dry-clean" maps to `FASHION` only. The menu offered when marking a product needs-work is the issue types mapped to that product's category. Seeded from the locked grid; editable as data later without code.

**Rejected: one denormalised row per (category, issue)**. Simpler to seed, but makes "Clean" several unrelated rows and loses the fact that it is one kind of work — which reporting across categories will want.

### 4. A focused remediation service, reusing the batch and ledger primitives

The transition is one operation — `changeState(product, lot, from, to, issueType?, quantity, at)` — on a new `GoodsRemediation` service rather than piled onto the already-large `GoodsInCounting`. It reuses the same batch-add and ledger helpers, so the append-only rules and MRP inheritance are not re-implemented. It: refuses a closed lot; refuses a quantity larger than the source batch holds; decrements the source batch; adds to the target batch (inheriting the product's MRP); and writes ledger entries only for the stock-bearing side(s) of the move.

The ledger entries follow from decision 1: a move out of a stock-bearing state writes a negative adjustment; a move into one writes a receipt/adjustment; a side that is off-ledger (`UNUSABLE`, `NEEDS_WORK`) writes nothing. So needs-work→Ready raises on-hand by one; Ready→scrap lowers it; the counts on both batches always move.

### 5. Entry points: count-into on the box screen, change-state by product, and a backlog

- **Counting into needs-work** — the unpacking condition step gains a "Needs work" outcome that then asks the issue type, filtered to the product's category.
- **Change-state later** — reached **by product, not by box**, since the carton is discarded. The operator scans or searches the item (reusing the camera resolve and the pricing screen's category-filtered product list), sees its state breakdown, and moves units — "cleaning: 3 → Ready" once wiped. This is the "search the scanned product, and based on category modify when treated/repaired" flow.
- **Prep backlog** — needs-work grouped by issue type (and category), rendered in the dashboard's existing list style, so the work is countable and routable.

## Risks / Trade-offs

- **On-hand excludes needs-work, so "owned" and "sellable" diverge** → Reports must not present on-hand as everything owned. Mitigated by the backlog view, which surfaces the held-but-not-sellable goods explicitly.
- **Adding a `StockCondition` value touches every exhaustive switch on condition and the batch CHECK constraint** → A missed branch is a silent bug. Mitigated by finding all `StockCondition` uses before coding and by widening the CHECK in a rebuild migration (as V17/V20 did for `DAMAGED`/`UNUSABLE`).
- **The nullable issue-type in a unique key is SQLite-specific** → NULLs are distinct in a plain unique index, which would allow two `GOOD` batches. Mitigated by a `COALESCE(issue_type,'')` unique index and a test that a second `GOOD` batch is still refused.
- **Off-ledger but cost-bearing is a new combination** → Close must include needs-work in the divisor while the ledger ignores it. Mitigated by a test that a needs-work unit carries the same unit cost as a `GOOD` one from the same lot.
- **Post-close rescue is unsupported** → A genuine rescue after a lot closes cannot be recorded. Acceptable now (all lots open); recorded as an open question, not designed around.

## Migration Plan

1. Widen the `batch` condition CHECK to include `NEEDS_WORK`, via a non-transactional rebuild (SQLite cannot alter a CHECK), and add the nullable `issue_type` column plus the `COALESCE` unique index.
2. Add `issue_type` and `category_issue_type` tables; seed both from the locked grid.
3. Include `NEEDS_WORK` quantities in the lot-close cost divisor, alongside `GOOD` and `DAMAGED`.
4. Add the `GoodsRemediation` service and its endpoints; wire the unpacking outcome, the by-product change-state screen, and the backlog view.

Rollback is `git revert` plus dropping the additive tables/column, safe until needs-work stock or a state change has been recorded against a lot that later closes.

## Open Questions

- **Post-close rescue** — when a closed lot first needs a unit rescued, how is the settled cost adjusted? Deferred until it happens.
- **Two problems on one unit** (dirty *and* missing a part) — modelled as one issue type per needs-work batch; the operator picks the primary. Revisit if it proves common.
- **Who maintains the issue-type grid** — seeded now; an admin editor is later work.
- **Does a move to Seconds during prep** (it turned out damaged) need anything beyond the existing damaged-price path? Assumed not.
