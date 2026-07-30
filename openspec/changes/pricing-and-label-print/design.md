## Context

The `barcode-printer-tsc-te244` change is done: the TSPL label is locked and printing on
the shop's TE-244 through `javax.print`, and `LabelTemplateService.renderRow(left, right)`
takes exactly `(barcode, name, mrp?, price)` per column. What is missing is the workflow that
produces real products for it. Today pricing (`PricingWorkbench`) is product-centric and
category-filtered; the print executor's `buildLabelRequest` is a stub that throws; the print
modal on the receiving screen renders an outdated cost/lot label.

The data model this builds on is settled: `Lot` (a delivery, with a FIFO cost to allocate),
`Batch` (product + lot + condition + quantity + `mrp`/`mrpIsEstimate`, with `sellingPrice()`
and `markLabelled()`), `Product` (name, category, nullable `sellingPrice`), `Barcode`
(Code 128, `BBZ-` prefix via `InternalBarcodeGenerator`), and an append-only
`StockLedgerEntry`. Margins already resolve category → price:
`TargetMargins.resolve(category, custom)` then `Margins.priceForTargetMargin(unitCost, %)`.

## Goals / Non-Goals

**Goals:**
- A lot-first pricing workbench: pick a lot, add a product to it (manifest-scan or manual),
  set name/description/optional-MRP/category/quantity, get a margin-suggested price, save.
- Saving mints the BBZ barcode, moves the quantity onto the shelf via the stock ledger, and
  records the label fields on the product.
- A self-contained print queue: the request carries the label fields; the executor renders
  without a database read. Remove the `itemType`/`itemId` + `buildLabelRequest` path.
- A per-product "label printed" marker, and a bulk-print screen listing shelf products that
  still need a label.
- A mobile capture interface (LAN, no login) that writes pricing-free captures into a review
  queue; a reviewer completes each in the workbench and approves it onto the shelf.

**Non-Goals:**
- AI category detection — category is chosen by a person from the lot's set (settled).
- Cross-session "hold the odd 2-up label" queue. Bulk printing pairs consecutively within a
  run and prints a lone leftover as a duplicate pair; a persistent hold-queue is deferred.
- Per-unit reconciliation of lost LSN references. Lost-LSN units are re-entered and the
  double-count is netted at the lot level (decision 3b), never traced to the original unit.
- Suggested prices for uncosted stock. Uncosted manual stock is hand-priced (decision 3).
- Photos on mobile capture — capture is text-only for v1 (name, optional MRP, description);
  image handling is left for later so SQLite is not loaded with blobs now.
- Auth on the mobile interface — single outlet, trusted LAN; deferred with the rest of auth.

## Decisions

### 1. The print request is self-contained; `PrintJob` denormalizes the label fields
`QueuePrintJobRequest` and the `print_job` row carry `barcode`, `productName`,
`sellingPricePaise`, `mrpPaise` (nullable), and `copies` — everything
`LabelTemplateService` needs. The executor renders straight from the row; `buildLabelRequest`
and the `itemType`/`itemId` columns are removed. A nullable `product_id` is kept **only** as a
back-reference: on a successful print the executor flips that product's label-printed marker
and the bulk screen can tell what is done. Rendering never reads it — the label reflects the
fields captured when the job was queued, so a later price change does not silently alter an
in-flight label; a reprint simply queues fresh values. This is the BREAKING change in the
proposal; `print_job` has no production rows yet, so the migration recreates it.

### 2. Lot-first pricing, reusing the margin machinery
A new `ShelfPricing` service (in `pricing`, alongside `PricingWorkbench`) drives the flow:
- `lots()` / `productsInLot(lotId)` — open lots, and the priceable items already in a lot
  (its batches' products), each with its FIFO unit cost.
- `categoriesForLot(lotId)` — the distinct categories present in the lot, for the dropdown.
  A mixed lot with nothing manifested yet returns empty, and the UI falls back to the full
  category list.
- `suggestPrice(lotId, productId, category, customMargin?)` — unit cost from the lot's batch,
  margin from `TargetMargins.resolve(category, custom)`, price from
  `Margins.priceForTargetMargin`. Only produced once a category is chosen.
- `save(...)` — sets the product's category and selling price (`Product.setSellingPrice`,
  the one sanctioned mutation), mints a BBZ barcode if the product has none
  (`InternalBarcodeGenerator.generateFor`), and moves the captured quantity onto the shelf.

### 3. Two ways in — pick an existing counted product, or manual-create
The workbench offers two paths, and the split is what guards against double-counting:
- **Pick existing** (`productsInLot`) — the primary path. Prices a product already counted into
  the lot (a costed batch). Covers manifested-and-found stock, and stock whose per-unit LSN
  was lost: the unit is re-identified by picking its product from the lot, not by scanning the
  dead LSN, and the minted BBZ becomes its new shelf identity. No new stock is created.
- **Manual-create** — a deliberate, separate action for stock **never counted** (mixed-lot
  discovery, or a lost-LSN unit that cannot be matched to any existing product). Creates a
  `Product` and a `Batch.counted(product, lot, condition, quantity, …)` so it has a lot and can
  be shelved. The UI nudges that manual-create is only for stock not already counted.

A manual batch created after the lot's cost was allocated is **uncosted** — it has no unit cost.
Per **option B**, the workbench then shows **no suggested price** for it and the operator types
the selling price by hand; the margin suggestion appears only for costed (counted-and-allocated)
stock. This is deliberate: a mixed lot's contents are unknown until entered, so its late manual
stock genuinely has no allocated cost to derive a margin from.

### 3a. Missing manifested stock is out of scope for pricing
A manifested item that was never counted has no batch, so `priceable`/`productsInLot` (which
list only costed batches) never surface it — pricing only ever shows on-hand stock. Its cost is
not lost: `LotClosing` already absorbs an uncounted line's share into the found batches, so the
found stock correctly carries a higher unit cost. Nothing new is built for this case.

### 3b. Phantom reconciliation is a lot-level write-off
Manual-creating a lost-LSN unit re-adds stock that is already counted in an orphaned batch the
operator cannot identify, so the double-count cannot be resolved per unit. It is netted at the
**lot** level: a lot-reconciliation step computes phantom = (counted) − (priced and shelved) and
writes the phantom off as shrinkage with a single append-only negative `StockLedgerEntry`, so
system stock equals physical reality and the loss is recorded against the lot. No ledger row is
edited — the write-off is a reversing entry, consistent with the append-only rule. Per-unit
reconciliation of a lost LSN is explicitly not attempted.

### 4. Quantity moves to the shelf through the stock ledger
Saving writes an append-only `StockLedgerEntry` for the captured quantity, the movement that
makes the units shelf inventory. Costing stays FIFO off the batch; selling price stays the
product's alone. No new costing rule — this reuses the existing ledger movement, the same one
counting already writes, at the pricing moment instead of the receiving moment.

### 5. `label_printed_at` on the product; bulk prints the un-marked
`Product` gains a nullable `label_printed_at` (Instant, ISO-8601 text, per the column
convention). The bulk screen lists shelf products where it is null; a bulk request queues a
self-contained job per product (from the stored label fields) and the executor stamps the
marker on success. Bulk pairs consecutive products into `renderRow` calls so a run of N
products prints ceil(N/2) rows; a lone leftover prints as a duplicate pair — no blank labels
within a run.

### 6. Mobile capture and the review queue
A new `ProductCapture` entity (draft, not a product): `name`, `mrpPaise` (nullable),
`description` (nullable), `lotId` (nullable — set on the phone if known, otherwise at review),
`status` (`pending`/`approved`/`rejected`), timestamps. Mobile endpoints under `/api/capture`
(served to a phone-friendly page on the shop LAN, no login) create captures. The review queue
lists `pending` captures; a reviewer opens one in the workbench pre-filled with its fields,
assigns the lot (if absent) and category, takes the suggested price, and approves — which runs
the same `ShelfPricing.save` and marks the capture `approved`. A capture carries no price and
never reaches the shelf on its own; approval is the only path from capture to product.

## Risks / Trade-offs

- **Denormalized label fields can drift from the product.** Intentional: the label reflects
  queue-time values, and reprints re-queue. The `product_id` back-reference keeps the
  printed-marker and bulk view correct without making rendering depend on live data.
- **No auth on `/api/capture`.** Anyone on the shop LAN can add a capture. Acceptable for a
  single trusted outlet; captures are pricing-free drafts that require a desktop reviewer to
  reach the shelf, so the blast radius is a junk review-queue row, not a mis-priced sale.
- **Category-from-lot can be empty** for a truly blind mixed lot. Handled by falling back to
  the full category list; the margin still resolves from whatever category the reviewer picks.
- **Bulk pairing is per-run, not global.** Two separate bulk runs of one product each still
  spend two rows. A persistent hold-queue would recover that; deferred as not worth the
  stateful complexity for now, and flagged so the limitation is visible rather than implied.
- **Removing `itemType`/`itemId` is breaking** for any queued job, but there are none in
  production and the receiving-screen modal that produced them is being retired in the same
  change.
