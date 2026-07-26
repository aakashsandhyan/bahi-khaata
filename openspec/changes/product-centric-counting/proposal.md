## Why

Counting a delivery is box-by-box on purpose: the manifest lists each carton's contents, a
returns sticker covers the printed barcode so every scan is matched to a line, and — the reason
box order cannot be abandoned — some goods carry no identifier at all and are only knowable as the
last line left in their box. That flow is correct, and it stays.

But it is slow whenever one product is spread across many boxes. The same product routinely arrives
on several cartons' sheets (and the same Amazon reference lands on several manifest rows), so a
product in ten boxes is ten separate lines, found ten separate times — each its own scan-or-search,
pane, and submit. The operator is holding a stack of the identical thing and the system makes them
count it ten times over.

The counting is already keyed at heart on `(product, lot)` — the per-box breakdown is the manifest's
guidance for *finding* goods, not a different quantity. So a product-centric way to count, sitting
on top of the box flow rather than replacing it, removes that repetition for any product that can be
identified — without touching the box-by-box path the unidentifiable goods depend on.

## What Changes

- A **lot filter on the catalogue**. Counting is per-lot — a lot is one delivery — so the catalogue
  gains a lot filter beside name, status, and department. Choosing the lot being counted scopes the
  list to that delivery's products and scopes each row's expected/counted units to that lot. This is
  a change to the shipped catalogue, not a new screen.
- A new **product-centric counting** view. With a lot chosen, the operator selects a product — by
  scanning any code that resolves to it, or by search — and sees **every box in that delivery that
  lists that product**, each with the quantity still expected and a field to enter what was found.
- Counts are entered **per box, on one screen**: the operator types the amount found against each
  box (`box A: 2, box C: 3`) and submits in one action. There is no single find-and-count per box,
  and no automatic guess of which box a unit came from — the per-box figure stays what the operator
  physically counted, so the box completeness picture stays true.
- One **condition** and one **MRP** apply to the whole submission (the common case: a stack of the
  same good stock). A damaged or needs-work unit in the stack is still counted on its own through
  the existing box flow, so condition is never averaged across units that differ.
- Both ways of counting **write the same lines and the same ledger**. A box is complete on the same
  terms as today — its lines counted (by either route) and the box opened — so nothing about
  completeness, opening, or the append-only ledger changes.
- **Restricted to goods with an identifier.** Product-centric counting is reachable only by resolving
  a code or matching a named product. A tagless item has nothing to resolve and is knowable only as
  the last line in its box, so it is deliberately **out of scope** here and stays box-centric. This
  is a stated boundary, not a gap.

Explicitly **not** in this change: no change to the box-by-box flow; no change to how tagless goods
are identified; no auto-distribution of a bulk count across boxes; no new stock states or schema.

## Capabilities

### New Capabilities
- `product-centric-counting`: finding a product once and recording, in one action, what was found of
  it across every box of an open delivery — a lane parallel to box-by-box counting for goods that
  can be identified, writing the same expected-lines and stock ledger.

### Modified Capabilities
- `product-catalog`: the catalogue gains a lot filter — choosing a lot restricts the list to that
  delivery's products and scopes each row's expected/counted units to that lot; found/on-paper then
  reflects that lot. With no lot chosen the catalogue is unchanged.

## Impact

- **Backend (`inventory`)**: a read that, for an open lot and a resolved product, returns every
  expected line for that product with its per-box outstanding quantity; and a count operation that
  takes a set of `(lineId, quantity)` entries plus one condition/MRP and records them against those
  lines — reusing the existing `countExpected` path and the append-only ledger, in one transaction.
  A product is resolved from a scanned code (existing barcode lookup) or a name search.
- **Contracts**: a DTO for "a product's box-lines in a delivery" (product identity + per-box
  outstanding) and a request for the per-box quantities submitted together.
- **Frontend (`dashboard/web`)**: a new product-centric mode in the unpacking screen — find by
  scan/search, a per-box quantity grid, a single condition/MRP, one submit. The submit-once and
  quantity work already in place are reused.
- **No schema migration.** Expected lines, batches, and the ledger are unchanged; this is a new way
  to write rows that already exist.
- **Restricts nothing existing.** The box-by-box flow, tagless identification, opening, and
  completeness are all untouched and remain the only route for unidentifiable goods.
