## Context

A product enters the catalogue when a manifest is read — `Product` holds `name`, `categoryCode`,
and `sellingPrice`, but **no marketplace reference of its own**; the ASIN lives as a `Barcode` row
with `Origin.MARKETPLACE`. A product is physically encountered later at unpacking, which leaves two
kinds of trace: a scannable `Barcode` (Origin `MANUFACTURER`, `UNIT_LABEL`, or `INTERNAL`) mapped
onto it, and/or a `Batch` counted against it. Neither trace is guaranteed alone — a plain count
against a manifest line creates a batch without a new barcode; a first-time tag creates a barcode
then counts. So "has the shop physically found this?" is the union of the two.

The building blocks already exist. `ProductRepository.findTop25ByNameContainingIgnoreCaseOrderByName`
does name search (capped at 25). `GoodsRemediation.statesOf(productId)` already returns a product's
name, category, and every batch state with quantities — a ready-made detail body — and lives in the
`inventory` package, which depends on `catalog`. `BarcodeRepository.findByProductId` and
`BatchRepository.findByProductId` give the two traces per product. Setting a price
(`POST /api/admin/pricing/products/{id}`) and mapping a code (the unpacking tag path) are existing
endpoints.

## Goals / Non-Goals

**Goals:**
- One name-searchable catalogue of every product, each row marked **found** or **on-paper**.
- Surface **on-paper** products (manifest-only, never found) as the default, since they are the gaps.
- Open a product to a detail that reuses the existing states view, and offer the price and
  map-a-code actions that already exist.
- Build the finder as the reusable picker the later product-centric-counting change selects through.

**Non-Goals:**
- The product-centric count grid (separate, dependent change). Here the catalogue only picks and hands
  off.
- Re-pointing Pricing's category list or remediation's search onto this finder (later, out of scope).
- Any new stock state, price rule, or schema column. Found/on-paper is derived at read time.

## Decisions

### 1. "Found" = has a Batch OR a non-MARKETPLACE Barcode
A product is **found** if `BatchRepository.findByProductId` is non-empty **or** it has any `Barcode`
whose origin is not `MARKETPLACE`; otherwise **on-paper**. Both traces are needed: a counted-but-not-
tagged product has a batch and no new code; a tagged-but-not-yet-counted product has a code and no
batch. The union catches both. `MARKETPLACE` is excluded because every product has one from import —
it is the on-paper marker itself, not evidence of being found.

### 2. Status is computed in SQL via EXISTS, so filter + paging stay in the database
Deriving status in Java after loading a page would make "on-paper first" impossible to page (you
cannot order by a value you compute after the query). Instead the filter is expressed as a JPQL
query with `EXISTS` sub-selects:

```
on-paper:  NOT EXISTS (batch for p) AND NOT EXISTS (physical barcode for p)
found:     EXISTS (batch for p)     OR  EXISTS (physical barcode for p)
```

with `name LIKE %:q%`, ordered by name, and a `Pageable`. This lets the three list modes — **on-paper
/ found / all** — each page correctly in the database. "On-paper first" is realised as the **default
filter tab**, not an interleaved sort across the whole set (see Risks). New repository methods take a
`Pageable`, replacing the hard `Top25` cap for the catalogue.

### 3. The catalogue service lives in `inventory`, not `catalog`
Computing found-status reads both `Barcode`/`Product` (catalog package) and `Batch` (inventory
package). The ArchUnit boundary allows `inventory → catalog`, never the reverse, so a service in
`catalog` cannot see `Batch`. `GoodsRemediation` already sits in `inventory` and reads products +
batches + barcodes for exactly this reason; the new `ProductCatalog` service follows that precedent
and lives beside it. The HTTP surface is still `/api/catalog`, so the URL reads by domain even though
the code sits with inventory.

### 4. Detail reuses `ProductStates`; row stays lean
The product detail composes the existing `statesOf(productId)` (name, category, per-condition batch
lines with quantities) plus the product's codes from `findByProductId`, and links the existing
set-price and tag actions. No new detail aggregation is written. The **list row** carries only
`productId`, `name`, `categoryCode`, `status` (FOUND / ON_PAPER), and `priced` (from
`sellingPrice != null`) — deliberately no stock sum, so the list query needs no per-product
aggregation; a units count, if wanted, is read in the detail.

### 5. Counting is a hand-off stub
The detail shows a **Count** affordance, but this change only wires it to select the product and
signal intent — the per-box count grid is the dependent `product-centric-counting` change. Building
the seam here (the picker returns a product; a placeholder action marks where counting attaches) is
what lets that change plug in without reworking the catalogue.

### New contracts
- `CatalogEntry(UUID productId, String name, String categoryCode, CatalogStatus status, boolean priced)`
  where `CatalogStatus` is `FOUND` | `ON_PAPER`.
- Detail reuses `ProductStates`; codes reuse a small `ProductCode(code, origin)` list (or an existing
  barcode summary if one fits).

### New surface
- `GET /api/catalog?q=&status=on-paper|found|all&page=&size=` → `List<CatalogEntry>` (paged).
- `GET /api/catalog/products/{id}` → detail (states + codes), or reuse
  `GET /api/remediation/products/{id}/states` for the states half.
- Frontend: a `catalog` value added to `App.tsx`'s `view` union, one nav button, one view; a
  `Catalog` component (search box, status tabs defaulting to on-paper, rows, a detail panel reusing
  the price and code actions); `api.ts` gains `catalog.browse` and `catalog.detail`.

## Risks / Trade-offs

- **On-paper-first is a filter, not a blended sort.** You cannot cheaply page a single list ordered by
  a derived status across thousands of rows. Realising it as a default **on-paper tab** gives the
  attention list directly and pages correctly, at the cost of not seeing found and on-paper
  interleaved in one scroll. Judged the right trade — the gaps are what matter, and a tab shows them
  cleanly.
- **EXISTS sub-selects per row.** With indexes on `barcode.product_id` and `batch.product_id` (foreign
  keys, already indexed) this is fine at the catalogue's scale (low thousands). If it ever bites, a
  derived/materialised flag is the escape — but that would add the schema this change avoids.
- **Status is a live snapshot.** A product flips from on-paper to found the instant it is first counted
  or tagged; a catalogue open in another tab shows the old status until refreshed. Acceptable — the
  catalogue is a lookup, not a lock.
- **Placement surprise.** Putting a "product catalogue" service in the `inventory` package reads oddly
  by name. The dependency direction forces it and the `GoodsRemediation` precedent matches; the design
  records it so it is a decision, not an accident.
- **Overlap with product-centric-counting.** The finder, the resolve, and the detail are shared. Built
  here once; the counting change consumes them. The risk is scope bleed — kept out by making counting
  an explicit hand-off stub, not a built action.
