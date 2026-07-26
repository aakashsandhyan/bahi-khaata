## Why

There is no way to browse the products the shop knows about. Every screen reaches a product through
its own narrow door — a scan at unpacking, a category list at pricing, a name search inside
remediation — and none of them answers the plain question "what is in the catalogue, and which of it
have we actually found?"

That second half matters most. A product enters the catalogue the moment a delivery's manifest is
read: a name, a category, a marketplace reference, and nothing else — no stock, no physical code. It
becomes real only when someone at unpacking physically encounters it: a code scanned onto it, a unit
counted into a batch. So the catalogue always holds two kinds of product — **found** and
**on-paper-only** — and the on-paper ones are exactly the goods a delivery still owes but nobody has
laid hands on. Today that gap is invisible unless you walk the boxes.

A single searchable catalogue makes both visible: find any product by name, see at a glance whether
it has been found yet, and act on it. The same finder is the front door the parked
**product-centric-counting** change needs — picking a product once, then counting it across its
boxes — so building it here, once, is what makes that feature possible.

## What Changes

- A new top-level **Catalog** view listing the shop's products, searched by **name filter** (reuses
  the existing name-contains lookup), each row showing name, category, and status.
- Every product carries a **found / on-paper** state, derived, not stored: **found** = it has a real
  physical code mapped (manufacturer, returns label, or internal BBZ) **or** at least one counted
  batch; **on-paper** = it exists only from the manifest — a marketplace reference, nothing counted,
  no physical code. **On-paper products are surfaced first**, since they are the goods still unfound.
- Selecting a product opens a **detail** with the actions valid for it — **set a price**, **map a
  code** onto it, and **view** its codes, batches, states, and the boxes it sits in.
- **Counting is not done from the catalogue directly.** Counting belongs to a box and an open lot, so
  the catalogue's role for counting is to be the **picker**: it selects the product and hands off to
  the product-centric counting flow (a later, dependent change). This change builds the finder and
  the hand-off point, not the count grid itself.
- A backend read that returns products by name filter with their **found / on-paper** status and
  enough summary (price set?, units in stock) to read the row, and the status of a single product for
  its detail.

Explicitly **not** in this change: the product-centric count grid (its own change, depends on this);
no change to how goods are scanned, counted, or identified; no new stock states; no schema change —
found/on-paper is computed from codes and batches that already exist.

## Capabilities

### New Capabilities
- `product-catalog`: browsing the shop's products by name, seeing which have been physically found
  versus exist only on a manifest, and opening one to price it, map a code, or view its detail — the
  shared product finder the rest of the app, and product-centric counting, select through.

### Modified Capabilities
<!-- None. This adds a new capability. Pricing and remediation keep their own entry points for now;
     folding them onto this shared finder is deliberately out of scope here. -->

## Impact

- **Backend (`catalog`)**: a read returning name-filtered products with derived **found / on-paper**
  status and a summary (priced?, units on hand), and a per-product status for the detail. Found is
  computed from a non-marketplace barcode or an existing batch — no new column. Reuses
  `findTop25ByNameContainingIgnoreCaseOrderByName`; a larger or paged variant may be needed so the
  catalogue is not capped at 25.
- **Contracts**: a `CatalogEntry` DTO (product identity + found/on-paper + priced + units) and a
  product-detail DTO (codes, batches, states, boxes) — some of this may reuse existing summaries
  (`ProductSummary`, remediation's `ProductStates`).
- **Frontend (`dashboard/web`)**: a new **Catalog** tab in the top nav — name search, found/on-paper
  filter and ordering, a product row, and a detail panel wiring the existing price and code-mapping
  actions, plus a "count this" hand-off stub for the dependent counting change.
- **No schema migration.** Found/on-paper is derived at read time from barcodes and batches.
- **Reuse target.** Built so product-centric counting (and, later, Prep/Pricing/remediation finders)
  select through this one catalogue rather than each rolling its own.
