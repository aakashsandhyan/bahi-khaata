# TSC TE-244 Barcode Printer Integration

## Why

Bachat Baazar receives bulk stock with no labels. Today operators manually write product info on items or use inconsistent labeling. This slows receiving, unpacking, and shelf placement. A dedicated thermal printer (TSC TE-244) eliminates manual work, ensures consistent labels with barcode + human-readable info, and speeds inventory flow from dock to shelf.

## What Changes

Add end-to-end barcode label printing powered by TSC TE-244:
- **Label format**: thermal 4x6 labels with barcode (Code128), product name, category, cost, MRP, lot/batch, expiry (if applicable)
- **Backend API**: print-job queue, label template rendering, printer communication (USB or network)
- **Frontend UI**: "Print Label" buttons in goods-in, unpacking, and catalog screens; batch print modal for multiple items
- **Printer setup**: configuration screen for printer address, paper size, copies, offline queue
- **Integration**: labels for received boxes (goods-in), unpacked batches (unpacking), and catalog products (pricing screen)

### New Capabilities

- **label-design**: 4x6 thermal label format with barcode, product details, cost/price tiers
- **print-backend-api**: REST API for print jobs, template rendering, queue management, retry logic
- **printer-config-ui**: admin screen to set printer address, test connectivity, manage queue
- **goods-in-print**: print labels for received boxes during goods-in workflow
- **unpacking-print**: print labels for unpacked batches (product + qty + lot)
- **catalog-print**: bulk print labels from catalog (e.g., price change labeling)

### Modified Capabilities

- **goods-in-workflow**: add "Print Label" button after receiving box
- **unpacking-workflow**: add "Print Labels" for counted batch
- **catalog-screen**: add batch print action to price changes

## Impact

- **Operator efficiency**: ~2 min saved per box (no manual writing)
- **Label consistency**: uniform barcode + info across all items
- **Shelf speed**: pre-labeled stock reduces placement time
- **Traceability**: barcode ties physical item to system record

## Risks

- Printer downtime (fallback: manual labels)
- Label paper supply chain (reorder thresholds needed)
- Thermal printer wear (maintenance schedule)
- Template design doesn't fit all scenarios (blanks for future extensibility)

## Acceptance

- Printer config page works and saves settings
- Print API queues jobs and communicates with printer
- "Print Label" button appears in goods-in, unpacking, catalog
- Labels render on thermal paper with readable barcode
- Batch print handles 10+ labels without timeout
- Offline queue persists jobs when printer unavailable
- Labels match visual identity (brand, font, spacing)
