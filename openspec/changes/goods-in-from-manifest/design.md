## Context

A first real consignment arrived in July 2026 — `SUSHIL BHOPAL 17-7.xlsx`, ₹945,662.95, 3,583 units across seven category lots — and importing it exposed the gap this change exists to close.

The importer built for `foundation-and-first-sale` treats goods-in as one act: it reads the manifest and writes stock straight onto the ledger. That is what ran, and the system now asserts 3,583 units are on hand on a supplier's word, with nobody having opened a box. It directly contradicts this change's own proposal, which says importing "records what is claimed, not what is held". The one-act importer was written first and the contradiction is ours to resolve, not a supplier's fault.

It matters more here than it would elsewhere. These are liquidation and returns goods; the premise of the whole change is that a manifest is an expectation and reality differs. A model that cannot express "the supplier says twelve, we found eleven" cannot describe the business.

Three facts about the real manifest shape the design:

- **Boxes are the unit of work.** Every sheet carries a `Tracking number` column as its first field — 533 distinct boxes across the consignment. The importer has been discarding it.
- **Most boxes are trivial.** The median box holds one line; 289 of 533 hold exactly one. A handful hold 60. Any design that assumes a long box-opening ritual gets the common case badly wrong.
- **Nothing is sellable yet.** All 1,878 products are unpriced and every batch lacks an MRP, because a manifest never carries the printed retail price.

The operator constraint is the sharpest one, in the user's words: *"The UX should be simple as the one operating it will be regular people."* This is the first screen a non-technical person uses all day.

## Goals / Non-Goals

**Goals:**

- Record a manifest as an expectation that puts nothing on hand.
- Let a person open a box, say what is actually inside, and have the system believe them over the manifest.
- Capture MRP from the goods, since it exists nowhere else and nothing may be sold without it.
- Apportion the lot's cost across what actually arrived, not what was promised.
- Make shortfalls and surpluses fall out of ordinary work rather than needing a separate audit.
- Keep a scan-driven screen operable by staff who have never heard of a batch.

**Non-Goals:**

- Label printing hardware. The states and the gate are in scope; driving a printer is not.
- Role-based permissions. Agreed as later work, and must be enforced server-side when it comes — hiding fields in the terminal is a curtain, not a boundary.
- Automatic repricing. Untouched; a delivery still never moves a selling price.
- ASIN lookup for MRP. Designed for as an assist, built later. Goods-in must never depend on the network.
- Multi-consignment reconciliation or supplier scorecards. One consignment at a time.

## Decisions

### 1. Import creates an expectation and a catalogue entry, but no stock

A manifest import writes the lot, a row per expected line, and the products themselves. It writes nothing to the stock ledger.

Products are created because a product is a catalogue entry, not a claim about stock — the unpacking screen needs a name to show and a barcode to match when someone scans. A product with no stock is already a legitimate state in this system. What import must not do is assert quantities nobody has counted.

**Rejected: keep the current one-act import** and treat any discrepancy as a later correction. It is less work and was already built. Rejected because it inverts the burden: the books would state a quantity as fact and rely on someone noticing it was wrong, in a business whose defining trait is that manifests are unreliable. A correction nobody makes is indistinguishable from correct data.

**Rejected: create products at unpack time instead**, from the scanned code. Rejected because the manifest already names them, and making staff type product names off packaging is exactly the data-entry burden this screen exists to avoid.

### 2. Cost is apportioned when the lot is closed, not at import and not per box

A lot's shares depend on every line in it, so no share can be final while boxes remain unopened. Batches therefore carry stock with no cost until the lot is closed, at which point the amount paid is apportioned across the quantities actually received.

This follows from what was actually paid: the lot amount was paid whatever turned up, so the goods that genuinely arrived must carry all of it. Eleven units that arrive where twelve were promised carry the whole line's cost between them, and cost a little more each. That is not an error to correct — it is what the shortfall cost.

A consequence worth stating: a product cannot be priced until its lot is closed, because a margin needs a cost. This aligns with the label gate rather than fighting it.

**Rejected: apportion per box as it closes**, so cost is known earlier. Rejected because every later box would change every earlier share, meaning continuous rewriting of costs that other records already reference.

**Rejected: apportion over expected quantities at import**, then adjust. Rejected because the adjustment is the hard part and would be owed on every lot.

### 3. A box is the tracking number, and it is the scan unit

Each expected line names the box it should arrive in. Scanning a box's tracking number is how someone starts work and how completeness is judged: what is still unopened, what arrived short.

The measured shape justifies the emphasis. With 289 of 533 boxes holding a single line, the overwhelmingly common interaction is: scan the box, scan the one thing in it, done. The screen must make that path two scans, not a workflow.

**Rejected: making the box optional** and unpacking straight into the lot. Rejected because the tracking number is free — it is already in the manifest and already printed on the carton — and without it "what have we not opened yet" has no answer.

### 4. A label carries the product's own code; the lot is reached through the batch

The label shows MRP, our price, and the saving. The code on it is the product's existing barcode — the supplier's code, already on the goods. Tracing an item back to a lot goes product → batch → lot.

**Rejected: a per-batch code** printed on every item. It gives exact traceability and exact per-item margin, which is genuinely better data. Rejected on the user's decision and because it means abandoning a code already printed on the pack in favour of printing a new one for every unit — a real daily cost for a reporting gain.

The trade-off must be stated plainly: when two batches of the same product are on the shelf at different costs, a scanned code does not say which one is in the customer's hand. FIFO decides, which is right for the ledger and approximate for that individual item. Per-lot margin stays exact in aggregate; per-item margin becomes an attribution rather than a fact.

### 5. Counting is recorded as it happens, and a half-open box is a normal state

Each count writes stock as it is found. Someone can stop mid-box, go home, and resume the next day with the box still showing what remains. Closing time is not an error condition.

**Rejected: a single "submit the whole box" action.** Rejected because it loses work on interruption, and interruption is the norm on a shop floor.

### 6. MRP is captured per batch, and remains the only gate on sellability

MRP is read off the goods and recorded against the batch, since successive deliveries of the same product genuinely arrive bearing different printed prices. Nothing may be sold without one.

An observed online price exists on the product and must never substitute for it — MRP is the printed legal ceiling and selling above it is unlawful, while a marketplace price is one seller's asking price on one day. This is already held by a test and this change does not weaken it.

### 7. The screen speaks the shop's language, not the schema's

No word from the data model appears: not batch, lot, allocation, FIFO, or ledger. The vocabulary is box, delivery, item, count, and the price printed on the pack. MRP itself stays — it is on every Indian pack and every shopkeeper knows it.

The scanner drives. Typing is for the MRP and for a count that is not one.

Two states need to be obvious without being read: an item that does not match anything expected, and a box that is finished. Both should be visible from across a room.

## Risks / Trade-offs

- **Stock exists before its cost does** → A batch carries units with no cost between counting and lot close. Valuation and margin must treat an uncosted batch as an explicit state rather than as zero. Pricing is already blocked by the label gate, so nothing can be sold at a wrong margin in the meantime.

- **The already-imported consignment is in the wrong model** → 1,878 products and 3,583 units were imported as on-hand receipts. Nothing has been sold, so this is fixable by re-import today and only today. The ledger is append-only and immutable by trigger; once sales exist, changing this needs reversing entries against live history.

- **A scan cannot say which batch is in the hand** → Accepted with decision 4. FIFO attributes it. Per-item margin is an attribution, not a measurement; per-lot margin remains exact.

- **Surpluses have no cost basis** → Goods that arrive but appear nowhere in the manifest still take a share of the lot, since the lot amount bought whatever came. They need a weighing value, and the manifest gives none. Recorded as an open question below.

- **533 boxes is a lot of scanning** → Mitigated by the shape: half are one line. The risk is a design tuned for the 60-line box making the one-line box tedious. The screen should be built against the median, not the maximum.

- **Staff may enter MRP wrongly, and it is a legal ceiling** → A wrong MRP is worse than a missing one, since selling above the real MRP is unlawful. Sanity checks against the observed online price can flag the implausible, but cannot verify. Left as an open question rather than assumed solved.

## Migration Plan

1. Add the expected-line and box tables; add lot state (open, closed) and the counted quantity on batch.
2. Extend `tools/consignment.py` to read the `Tracking number` column it currently discards.
3. Change the importer to write expectations and products, and no ledger entries.
4. Wipe and re-import the Sushil consignment. Nothing has been sold and no MRP or price has been entered by hand, so nothing is lost.
5. Build the unpacking screen against the re-imported data.

Rollback is `git revert` plus a re-import, for as long as no sale has been recorded. After the first sale, rollback is a data migration and this window closes.

## Open Questions

- **What weighs a surplus line?** Goods that arrive unlisted must take a share of the lot cost, but the manifest gives no value for them. Options include the average unit value of the lot, an operator estimate, or holding them uncosted until someone decides. Not settled.
- **How is a wrong MRP caught?** A typo in a legal ceiling has legal consequences. A plausibility check against the observed online price is possible but is not verification.
- **Category margins are still placeholders**, as are `pricing.target_margin_percent` and the cart staleness backstop. Real Bachat Baazar figures are needed before pricing can be trusted, and this change makes pricing reachable.
- **`RELATIVE_MRP` is now a misleading name** for a method no longer weighted by MRP; renaming it to name the weight honestly (supplier cost, market price) was proposed and is undecided.
- **The negotiated factor per lot is recoverable and currently discarded** — 0.25 of Amazon price for three categories, 0.20 and 0.35 for two others, cost plus 10% for the rest. Whether to store it, and whether to validate it at import, is undecided.
