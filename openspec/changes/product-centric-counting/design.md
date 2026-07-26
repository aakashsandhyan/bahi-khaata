## Context

Counting a delivery is box-by-box, and rightly so — tagless goods are knowable only as the last line
left in their box. But one product routinely sits on many boxes' sheets (the same marketplace
reference lands on several manifest rows), so a product in ten boxes is ten separate finds. The
operator holds a stack of the identical thing and counts it ten times.

The pieces to fix this now exist. The **product catalogue** (shipped) is the shared finder: it
resolves a product by scanned code or name and already carries a "Count" affordance that is a
hand-off stub waiting for this feature. Counting itself runs through `GoodsInCounting.countExpected`
against `ExpectedLine` and the append-only ledger — both stable. `ExpectedLine` has a real FK to its
`Box` and carries `quantityExpected` / `quantityCounted`, so a product's outstanding units per box
are a straight read.

One deliberate boundary: the receiving-workflow rewrite is in flight and left `BoxReceipt` (received
/ unpacked state, which gates lot close) **disconnected** from counting — counting a box marks
`Box.lastActivityAt` but never touches its `BoxReceipt`. This feature does **not** reach into that.
It reuses `countExpected` exactly as box-centric counting does, writing lines and ledger and nothing
else, so it stays clear of the unfinished receiving work and behaves identically to the existing path
on receiving state.

## Goals / Non-Goals

**Goals:**
- Find a product once (via the catalogue — scanned code or search) and count what was found of it
  across every box of an open delivery, in one submission.
- Enter counts **per box** on one screen; never guess which box a unit came from.
- One condition and one MRP for the submission (a stack of like stock); reuse the existing count path
  and ledger; safe against two stations counting the same box at once.

**Non-Goals:**
- Tagless goods (no identifier to resolve) — they stay box-centric, by nature.
- Marking a box received / touching `BoxReceipt` — descoped; left to the box flow and the receiving
  rewrite. A product-centric-counted box is marked received exactly when a box-centric-counted one
  is: not by counting.
- Auto-distributing a bulk count across boxes; averaging condition across unlike units; any schema
  change.

## Decisions

### 0. A lot filter on the catalogue is the entry
Counting is per-lot: a lot is one delivery, and the operator is working one delivery at a time. So
the catalogue gains a **lot filter** alongside name, status, and department — pick the lot being
counted and the list scopes to the products in it. This is a modification to the shipped
`product-catalog` capability. When a lot is chosen:
- the list is restricted to products with an expected line in that lot;
- the row's expected/counted units are summed **over that lot only**, not across every delivery;
- found/on-paper reflects that lot — found means a unit has been counted in it.
With no lot chosen the catalogue behaves as today, spanning all deliveries. A product can sit in
several open lots, so the lot filter is what says which delivery's copy is being counted.

### 1. The catalogue is the front door
With a lot chosen, the operator selects a product and the "Count" action — a stub until now — opens
its per-box grid **for that lot**. The catalogue already resolves by scanned code or name and now
carries the lot, so no new finder is built and the lot is never ambiguous.

### 2. A code resolves to exactly one product
A manufacturer/variation code can sit on sibling products (the same item under several marketplace
references). A scan resolves to **one** product and shows only that product's lines; it does not
gather siblings. Collapsing sibling rows is a pricing-time merge, out of scope here. This keeps a
count landing where the operator pointed and avoids the misdistribution the double-count bug showed.

### 3. The grid: a product's outstanding lines in the open lot
A backend read takes an open `lotId` and a `productId` and returns every `ExpectedLine` for that
product in that lot with its box tracking number and **outstanding** (`quantityExpected -
quantityCounted`). Lines already full drop out. Only open lots — a closed lot is not being counted.

### 4. Submit: per-box quantities, one condition/MRP, capped, with an optimistic concurrency check
The submission is a set of `(lineId, quantity)` entries plus one condition, one MRP, and the MRP-
estimate flag. For each entry:
- The quantity is **capped at that line's current outstanding** (min 0). Bulk counting cannot push a
  line over its expectation — the guard the overcount cleanup earned.
- Each entry carries the **outstanding the client saw when the grid loaded**. The backend re-reads
  the line's current outstanding in the submit transaction; if it has changed (another station
  counted into that box meanwhile), that entry is **refused** and its new outstanding returned, for
  the operator to re-enter. Accepted entries still commit. This is optimistic concurrency — no lock,
  just a compare — and it is what stops two laptops double-counting one box.
- Accepted entries each run through the existing `countExpected(lineId, condition, quantity, mrp,
  estimate, remark, issueType, at)`, in one transaction, so batch, ledger, and MRP inheritance are
  written exactly as box-centric counting writes them.

### 5. One condition/MRP per submission; damaged handled in the box flow
The common case is a stack of sound stock: one condition, one MRP for all of it. A damaged or
needs-work unit in the stack is still counted on its own through the box flow, so a differing
condition is never averaged over the group.

### 6. Identified goods only
Reachable only for a product the catalogue could resolve — a real code or a named match. An unknown
first-scan (mapping a new code to a line) stays the box flow's `tagAndCount`; a tagless item stays
box-only. "Unknown code" is not "tagless", but both are out of scope here.

### New contracts
- `ProductLotLines(UUID productId, String productName, UUID lotId, List<ProductBoxLine> lines)` where
  `ProductBoxLine(UUID lineId, String boxTracking, long outstanding)`.
- `ProductCountRequest(UUID productId, UUID lotId, StockCondition condition, Long mrpPaise, boolean
  mrpIsEstimate, List<BoxCountEntry> entries)` where `BoxCountEntry(UUID lineId, long quantity, long
  outstandingSeen)`.
- `ProductCountResult(int linesCounted, long unitsCounted, List<RejectedEntry> rejected)` where
  `RejectedEntry(UUID lineId, String boxTracking, long nowOutstanding)`.

### Modified surface (product-catalog)
- `GET /api/catalog` gains an optional `lot` parameter. When present, the list is restricted to
  products with an expected line in that lot and the entry's `unitsExpected`/`unitsCounted` are summed
  over that lot; found/on-paper reflects that lot. Absent, behaviour is unchanged.
- A lot list for the filter: reuse the existing deliveries read (`GET /api/unpacking/deliveries` gives
  each open lot with supplier, category, and progress) rather than mint a new one.

### New surface (product-centric-counting)
- `GET /api/product-counting/lots/{lotId}/products/{productId}/lines` → `ProductLotLines`.
- `POST /api/product-counting/count` → `ProductCountResult`.
- A `ProductCounting` service in the inventory package (reuses `GoodsInCounting.countExpected`,
  `ExpectedLineRepository`, `LotRepository`), beside the box-flow services.
- Frontend: a lot filter on the catalogue, and a product-centric grid reached from the catalogue's
  Count action with that lot — per-box quantity fields, one condition/MRP, one submit; the
  submit-once and quantity work already built are reused.

## Risks / Trade-offs

- **Concurrency rejection is a real UX path, not an error.** When another station moved a line, that
  entry comes back refused with its new outstanding; the screen must show it plainly and let the
  operator re-enter, not read as a failure. Accepted entries still committed.
- **Descoping box-received leaves a known seam.** A box counted only product-centrically is *not*
  marked received, exactly as box-centric counting does not mark it — so lot close still depends on
  the box/receiving flow reconciling receipts. This is deliberate: wiring counting → `BoxReceipt`
  belongs to the receiving rewrite, and doing it here would couple to unfinished, actively-changing
  code. When receiving lands, one change wires both counting paths together.
- **Open-lot only.** A product spanning a closed lot and an open one shows only the open lot's lines;
  a closed lot is done. Reasonable, but worth stating so a missing box is not mistaken for a bug.
- **Sibling ASINs stay separate.** Counting resolves to one product; a sibling row is counted on its
  own. Correct until the pricing-time merge, but it means the same physical item can wear two rows in
  the grid across two products — expected, not a duplicate.
