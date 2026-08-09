## Why

Bachat Bazar is registered under the **regular** GST scheme, but the shipped system is wrong for a regular dealer in two contradictory ways: the printed receipt declares the shop a *composition* dealer ("Bill of Supply", collects no tax), while the till separately **adds** a flat 18% on top of a selling price that is already MRP-inclusive — so a ₹510 item rings ₹601.80 and can exceed its own MRP. A regular dealer must instead issue a **Tax Invoice** with GST **extracted inclusively** from the selling price. The shop is pre-launch — **zero sales recorded** — so this is the moment to make the first invoice correct, before any immutable sale exists. This implements the tax-invoice and inclusive-GST behaviour anticipated in the report (§3.4, §4) and closes the open CA-rate question (§11.3) with a runtime-editable rate table rather than hard-coded slabs.

## What Changes

- **Checkout stops adding GST on top.** GST is extracted inclusively from the MRP-inclusive selling price (`total = subtotal`), split into **CGST + SGST**, and rounded at the invoice total (§170, nearest rupee). **BREAKING**: the cart/sale tax contract changes — the `taxIsPlaceholder` stand-in is retired and a real tax breakdown appears.
- **A product carries a controlled `sub_category`** (parent = its `category`) that determines its GST rate. A single per-category rate is provably wrong for this catalogue: KITCHEN (845 products) mixes metal utensils at 5% with plastic/glass and appliances at 18%, and apparel/footwear rate depends on the item's price. The `sub_category` is the rate-bearing classification.
- **GST rate is derived from `sub_category` via a runtime-editable, effective-dated rate table** — rates change over time (GST 2.0 collapsed the slabs to 5 / 18 / 40 in September 2025), so the table keeps history and is edited by an admin, never by a code change. The resolver falls back to a global default (18%) when a product is unclassified.
- **The resolved rate is snapshotted onto the sale at ring time**, so a later rate correction can never move the tax owed on a completed, immutable sale (§3.4).
- **Existing products are classified best-effort** by a one-time name-heuristic backfill; low-confidence items are deliberately left unclassified. An unclassified item **blocks and prompts once at the till** for its sub_category (then persists) — it is **not** a hard sellability gate, so classification can also happen at pricing time.
- **The receipt/bill flips from composition "Bill of Supply" to a Tax Invoice** printing the CGST + SGST breakdown; `BillSettings` defaults (title, declaration) change accordingly. **BREAKING**: the printed document changes.
- **Admin gains a GST-rate screen** (per sub_category, editable, effective-dated) plus a sub_category vocabulary, mirroring the existing category-margins admin.

## Capabilities

### New Capabilities
- `gst-taxation`: the `sub_category` rate-group vocabulary, the effective-dated `sub_category → rate` table and its resolver (with the global default fallback), the inclusive-extraction + CGST/SGST split + invoice-level rounding arithmetic, the admin API/screen for editing rates, and the one-time name-heuristic classification backfill.

### Modified Capabilities
- `checkout`: cart and sale tax become inclusive-extracted with a CGST/SGST breakdown snapshotted per line; the till prompts once for an unclassified item's sub_category before it can be rung.
- `receipt-print`: the printed document becomes a Tax Invoice showing the GST breakdown; the composition "Bill of Supply" wording is retired from the defaults.
- `product-catalog`: a product gains a controlled `sub_category` attribute (parent = category), populated by the backfill and editable in admin.

## Impact

- **Backend**: `Checkout` (tax math), `Cart`/`CartLine`, `Sale`/`SaleLine`, `ReceiptTemplateService`, `BillSettings`, `Product`, plus new sub_category + rate entities, the resolver service, the classification backfill, and admin controllers.
- **Contracts**: `CartView`, `CartLineView`, `SaleView`, `SaleLineView` gain tax fields (CGST/SGST/taxable value); bill-settings view/text change.
- **Migrations** (next is **V47+**): `sub_category` reference table; `product.sub_category`; effective-dated `gst_rate` table with a partial-unique index on the active row; tax columns on `cart_line` and `sale_line`; updated `bill_settings` defaults/seed.
- **Frontend** (`dashboard/web`): the till shows the incl-GST breakdown; new sub_category and GST-rate admin screens; pricing surfaces the classification.
- **Data**: shop is not live (0 sales), so there is **no historical-sale migration**; the backfill touches only product classification.
- **Explicitly unchanged** (report §10, §4.1): no e-invoicing/IRP (B2C is exempt regardless of turnover), no HSN capture (the `hsn_code` column stays null — HSN is not mandatory for B2C below ₹5 cr), no IGST/inter-state handling (single-outlet, same-state B2C counter → CGST+SGST only).
