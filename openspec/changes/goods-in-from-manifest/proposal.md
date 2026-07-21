## Why

A real consignment arrived and the model did not fit it.

`foundation-and-first-sale` assumes goods-in is one act: you know what arrived, at what cost, and record it. Reality is two acts separated by hours or days. A supplier sends a workbook saying what is coming; later, boxes are opened and what is actually inside is discovered. Those disagree, and the difference is the point — a manifest is an *expectation*, not a record of fact.

The first consignment also broke three assumptions outright, each of which has already forced a change:

- **A third of its units had no category** in the governed six. The enum became a lookup table.
- **Two of its seven categories carry no retail price at all** — they are priced off supplier cost, not off retail. MRP became optional, and stopped doubling as the cost-apportioning weight.
- **A rounded spreadsheet export silently shifted the retail total by ₹52**, which took an afternoon to find. Precision at the boundary is not a detail.

Nothing can be sold from this delivery until it is unpacked, identified, priced and labelled. This change is that path.

## What Changes

- **A manifest is imported as an expectation.** A workbook with a sheet per category becomes one lot per category, each with what was paid, and a set of expected lines. Nothing is on hand yet: importing records what is claimed, not what is held.
- **Unpacking records fact.** Boxes are opened, products identified, quantities counted, and MRP read off the goods. Stock enters the ledger as it is found, not as it was promised.
- **Cost is apportioned at reconciliation**, once actual quantities are known — not at import. The lot amount was paid regardless of what turned up, so it is the received goods that must carry it.
- **A product becomes sellable only once labelled**, and can only be labelled once it has both a price and an MRP. This makes the label the gate onto the shop floor, as report §5.2 requires.
- **A tracking number identifies a physical box**, carrying one or more expected lines. Scanning it is how completeness is checked: what is still unopened, what arrived short.
- **MRP is captured from the goods**, with an ASIN-based lookup as an assist where a tag is missing. A looked-up figure is always marked an estimate.
- **The supplier's own category codes are mapped on import**, so nobody matches names by hand.

**BREAKING**: `GoodsInService.receive()` assumes a complete, known delivery in one transaction. That remains valid for a small hand-entered delivery but is no longer the primary path, and the reconciliation flow supersedes it for consignments.

Deliberately **not** in this change: label printing itself (hardware, and out of scope for the current change), role-based permissions (agreed as later work), and any automatic repricing.

## Capabilities

### New Capabilities

- `consignment-manifest`: What a supplier's workbook claims — lots, expected lines, what was paid for each category, and the two pricing schemes those categories use. Import, mapping of supplier category codes, and the guarantee that importing an expectation puts nothing on hand.
- `goods-in-reconciliation`: Turning an expectation into fact. Opening boxes, recording what was actually found against what was expected, capturing MRP, and apportioning the lot's cost across the goods that genuinely arrived. Includes shortfalls, surpluses, and completeness by box.
- `shelf-readiness`: What must be true before stock may be sold — a price, a recorded MRP, and a printed label. The states a product passes through between arriving and reaching the floor, and what each of them permits.

### Modified Capabilities

- `product-catalog`: MRP becomes optional and is no longer the cost-apportioning weight; a product needs both a price and an MRP to be sellable; categories are a governed set of rows rather than fixed values.
- `stock-ledger`: Cost apportionment takes its weight as an input rather than reading MRP, so a manifest stating a selling price, a supplier cost, or nothing at all can all be handled.

## Impact

- **New tables**: expected consignment lines, boxes keyed by tracking number, and the labelled state. `category` and `category_margin` already exist.
- **New terminal screens**: unpacking is the first screen a non-technical person uses all day. The overriding constraint is that it must be operable by regular staff — no term from the data model appears on it, the scanner drives rather than the keyboard, and a half-unpacked box at closing time is a normal state to walk away from rather than an error.
- **Tools**: `tools/consignment.py` and `tools/xlsx_to_csv.py` already read these workbooks and reconcile them. They are the reference for what the importer must handle, including thousands separators, sub-paise amounts, and rounded exports.
- **External dependency**: an ASIN lookup for MRP, used only as an assist. It must never be required for goods-in to proceed, since checkout and receiving cannot depend on the network.
