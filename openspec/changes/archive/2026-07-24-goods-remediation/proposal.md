## Why

Liquidation goods rarely arrive shelf-ready: an induction works but is filthy, a kettle is sound but missing its base, a shirt is perfect but creased. The model holds only Ready / Seconds / Scrap, so such stock is forced into DAMAGED — underpricing full-value goods as seconds — or GOOD — putting dirty, incomplete items on the shelf. Neither is true. And once a unit's state is recorded there is no way to change it, so an item rescued at the bench cannot be made sellable in the books.

## What Changes

- Add a **needs-work** state for stock that arrived, is functional or fixable, and will sell at **full price once prepped** — distinct from DAMAGED (worth less, permanently) and from GOOD (shelf-ready now). It is not sellable until the work is done.
- Attach an **issue type** to each needs-work unit, drawn from a menu **scoped to the product's category** — dry-clean for clothes, rebuild for appliances. Issue types are data-driven rows keyed to category, the same choice already made for `Category` itself, not a fixed enum.
- Add a **change-state** action that moves units between states as reality changes: needs-work → Ready when prepped; the rescues already happening by hand (DAMAGED or Scrap → Ready when a unit is cleaned or rebuilt from another's parts); and the reverse (Ready → DAMAGED/Scrap when damage is found later).
- Surface a **prep backlog** — how many units wait on cleaning, repair, rebuild, repackaging — so the work is routable to whoever does it, instead of living in someone's memory.
- No data migration is destructive: the new state and the issue-type table are additive, and existing GOOD/DAMAGED/UNUSABLE stock is untouched.

## Capabilities

**New Capabilities:**
- `goods-remediation` — the needs-work state, the per-category issue-type taxonomy, the change-state transitions between all stock states, and the prep-backlog view.

**Modified Capabilities:**
- None. `openspec/specs/` holds no synced specs yet; the goods-in behaviour this builds on was delivered under the completed `goods-in-from-manifest` change and is referenced, not modified here.

## Impact

- **Model** — a needs-work state alongside GOOD / DAMAGED / UNUSABLE, and an issue-type table keyed to product category, with the chosen issue recorded against the held units. Category was already made a table precisely to allow category-specific behaviour like this.
- **Counting service** (`GoodsInCounting`) — counting can land a unit in needs-work with an issue type; a new transition operation moves units between states.
- **Stock ledger** — transitions are append-only adjustments on the stock-bearing states (GOOD, DAMAGED); UNUSABLE stays off-ledger, so on-hand rises when scrap is rescued and falls when a unit is scrapped. No edits, no rewrites — consistent with the ledger's immutability.
- **Cost** — needs-work carries ordinary lot cost, since it sells at full price; nothing crosses products or lots. All seven lots are currently open, so cost still settles at close over final counts, with no re-costing of settled lots.
- **UI** — the unpacking screen gains a needs-work outcome with the category-scoped issue menu; a change-state control reached by product (so it survives the carton being discarded); and a backlog view for the prep piles. The terminal/dashboard boundary is unchanged.
- **Sellability** — the gate is unchanged (priced + MRP + labelled + a sellable state). Needs-work is simply excluded until moved to Ready.
