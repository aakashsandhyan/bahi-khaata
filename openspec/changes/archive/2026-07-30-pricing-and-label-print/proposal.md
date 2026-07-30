## Why

Pricing is the moment a received item becomes a sellable, shelf-ready product — it gets a
name, a category, a selling price, a barcode, and a printed label. Today that moment is
scattered: the label print modal lives on the receiving screen, prints an outdated cost/lot
label, and there is no single place to turn a lot's contents (whether manifested or a mixed
unknown) into priced, barcoded, shelf inventory. The label hardware and template are now
proven; what is missing is the workflow that feeds them real products.

## What Changes

- A **lot-first pricing workbench** becomes the hub for making a product sellable: pick a lot,
  then add a product to it — either by scanning one already manifested into the lot, or by
  entering a mixed-lot item by hand. Both paths tie the product to the lot, so its FIFO unit
  cost (and therefore its margin) is known.
- Pricing a product captures: name and description, **optional MRP**, a **category chosen from
  the categories that lot actually contains**, a **quantity**, and a **selling price
  auto-suggested from the category's target margin** against the lot's unit cost (reusing the
  existing margin machinery — no AI, the suggestion only appears once a category is chosen).
- Saving a product **mints its BBZ barcode**, moves its quantity onto the shelf as inventory,
  and stores the label fields on the product so it can be reprinted later.
- After save, the user is offered **"print label?"** which queues the locked TSPL label.
- **BREAKING**: the print queue request becomes **self-contained** — it carries the label
  fields (barcode, name, selling price, optional MRP) directly, and the executor renders them
  without a database lookup. The current `itemType`/`itemId` + `buildLabelRequest` DB-lookup
  path (never finished — it throws "not yet implemented") is removed.
- A **bulk label-print screen** lists shelf products whose label has not yet been printed and
  queues them together, for reprints and for products priced without printing at the time.
- A **mobile capture interface** lets people key raw product info (name, optional MRP, optional
  description/photo) from a phone on the shop network. Captures carry **no pricing** — they land
  in a **review queue**; a reviewer on the desktop workbench pulls each one, assigns the lot and
  category, gets the margin-suggested price, mints the barcode, and approves it onto the shelf,
  from where it can be bulk-printed. Mobile is a field feeder into the same pricing pipeline, not
  a second pricing path.
- The receiving screen's print modal (cost/lot label) is retired; labelling moves to pricing.

## Capabilities

### New Capabilities
- `shelf-pricing`: the lot-first pricing workbench — select a lot, add a product to it by
  manifest-scan or manual entry, set name/description/optional-MRP/category/quantity, get a
  margin-driven suggested selling price, and on save mint a BBZ barcode and move the quantity
  onto the shelf.
- `label-print`: a self-contained label print queue whose request carries the label fields
  with no database lookup at print time, plus a bulk-print screen for products not yet
  labelled and reprints.
- `mobile-capture`: a phone-friendly, LAN-accessible interface (no login, single outlet) for
  capturing raw product info into a review queue with no pricing, and the review queue itself —
  where a desktop reviewer completes lot/category/price and approves a capture onto the shelf.

### Modified Capabilities
- `product-catalog`: shelf products gain a **label-printed** marker so the bulk screen can find
  what still needs a label, and the catalog reflects products created through pricing.

## Impact

- **Backend** — new `shelf-pricing` flow (reuses `pricing` margin machinery, `catalog`
  barcode generation, `inventory` lot/batch and stock ledger); `print` package: replace the
  `itemType`/`itemId` `PrintJob` payload and stubbed `buildLabelRequest` with self-contained
  label fields; add a product-level label-printed flag and a "not yet labelled" query.
- **Contracts** — `QueuePrintJobRequest` changes shape (self-contained label fields); new
  request/response types for the pricing workbench and bulk print.
- **Frontend** — new pricing workbench screen (lot picker, add-product manifest/manual,
  category-from-lot, suggested price, quantity, save + print prompt); new bulk-print screen;
  new mobile capture screen (phone layout) and a review-queue screen; retire the
  receiving-screen print modal.
- **Migrations** — `print_job` columns for the self-contained fields; a product label-printed
  marker.
- Ships on the proven TSPL label and `javax.print` transport from the `barcode-printer-tsc-te244`
  change — this change feeds them real product data.
