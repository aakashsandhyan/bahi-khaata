## Context

The pricing workbench is keyed entirely on batches (counted stock). `ShelfPricing.lots()`
returns `lots.findAllById(batches.lotIdsWithUnpricedStock())` where the query is
`select distinct b.lot.id from Batch b where b.product.sellingPrice is null`, and
`categoriesForLot()` returns the categories found on that lot's batches. A lot with no counted
stock has no batches, so it is absent from the picker and — even if surfaced — offers no
categories to hand-price under. Hand-add (`saveManual`), the intended no-count path, creates the
batch itself but is only reachable after a lot is selected. See `proposal.md` and the modified
`shelf-pricing` requirement.

The `shelf-pricing` spec already requires the category fallback ("falling back to the full
category list when the lot has none yet"); the code simply never implemented it. So one half of
this change is a drift fix, the other half a genuine requirement change to the lot list.

## Goals / Non-Goals

**Goals:**
- A just-added, uncounted lot appears in the pricing lot picker and can be hand-priced into.
- The hand-add category picker is usable for a batch-less lot (full category list).
- No schema change; reuse the existing hand-add stock-creation path.

**Non-Goals:**
- Any change to counting, receiving, manifest import, or the manual-lot expectation flow.
- A formal "finish this lot" affordance for mixed lots (they persist in the picker until closed —
  noted as a trade-off, not solved here).
- Frontend changes beyond what starts working automatically.

## Decisions

### Which lots the picker lists
List a lot when it is **open**, OR it has **counted-but-unpriced stock**. Concretely:
`open lots ∪ lots in lotIdsWithUnpricedStock()`, de-duplicated by id, sorted by `receivedOn`
descending (unchanged sort). Implementation: fetch `LotRepository.findByState(OPEN)` and
`lots.findAllById(lotIdsWithUnpricedStock())`, merge into a `LinkedHashMap<UUID, Lot>`, map to
`ShelfLot`. Small data, single-writer local DB — two queries + a merge is the reviewable choice
over a hand-written UNION JPQL.

This satisfies the modified requirement: open lots (including empty, uncounted ones) stay listed
until closed; closed lots appear only while they still hold unpriced counted stock.

*Alternative considered:* restrict the newly-surfaced empty lots to `isManual` lots only. Rejected
— it adds a special-case predicate, and an open manifest lot is also a legitimate place to
hand-price a found item. "Open OR has unpriced counted stock" is the simpler, coherent rule.

### Category fallback for a batch-less lot
`categoriesForLot()` returns batch-derived categories today. Change: when the lot has no batches,
return the **full list of category codes** from the category lookup table (the same source the
rest of the app uses for category choices). This is exactly what the existing spec already
mandates; the frontend already consumes whatever `categoriesForLot` returns, so no UI change.

### Frontend
None. `PricingWorkbench` renders `lots()` verbatim and already exposes "+ Add by hand" once a lot
is selected. With the two backend changes, selecting an empty lot and hand-pricing simply works.

## Risks / Trade-offs

- **Open manifest lots awaiting count now also appear on pricing** → mild picker noise. Mitigation:
  acceptable — they are legitimately hand-price-able, and the operator can still choose to count
  them via Unpacking. A future "needs counting" badge could distinguish them; out of scope here.
- **An open mixed lot never closes → stays in the picker indefinitely** → the shop has no formal
  "done with this lot" step for uncounted lots. Mitigation: accepted for now; lot close via
  reconciliation already removes it. A lightweight close affordance is a possible follow-up.
- **Category fallback surfaces every category for a batch-less lot** → longer dropdown, but that is
  the specified behaviour and matches hand-adding an arbitrary mixed item.

## Migration Plan

Pure behaviour change in two service methods; no data or schema migration. Forward-only; reverting
the code restores the prior picker behaviour.

## Open Questions

- Should open manifest lots (with an expectation but no count) be visually distinguished from
  hand-priced mixed lots in the picker? Deferred — cosmetic, not blocking.
