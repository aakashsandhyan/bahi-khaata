## Context

Bachat Bazar is a **regular** GST dealer, but the shipped system contradicts itself: the receipt
prints a composition "Bill of Supply" (no tax), while checkout adds a flat 18% on top of an
already-MRP-inclusive price. The shop is pre-launch (0 sales), so no immutable invoice exists yet —
this is the moment to make the first one correct. See `proposal.md`; the modified capabilities are
`checkout`, `receipt-print`, `product-catalog`, and the new one is `gst-taxation`.

Grounding facts:
- Indian MRP is **tax-inclusive by law** — the shelf/selling price already contains GST. So a
  regular dealer *extracts* GST from the price, never adds it on top.
- Single Bhopal outlet, B2C walk-in → place of supply is same-state → **CGST + SGST** only (never
  IGST). An 18% item = 9% CGST + 9% SGST.
- GST 2.0 (22 Sep 2025) collapsed slabs to **5 / 18 / 40** (+0%); rates change over time, so they
  must be data, not code.
- Rate follows HSN/material/price, **not** a shop category — KITCHEN (845 products) mixes metal
  utensils (5%) with plastic/glass/appliances (18%), and apparel/footwear split by price. The live
  catalogue proves a single per-category rate mis-taxes ~40%+ of KITCHEN. Products carry **no HSN**.
- Existing patterns to mirror: `category_margin` table + `TargetMargins` resolver (per-category,
  runtime-editable, global default); the Sale/SaleLine snapshot spine and `BillSettings` from
  bill-printing; the immutability rule (report §3.4, checkout "A completed sale is immutable").

## Goals / Non-Goals

**Goals**
- A correct **Tax Invoice**: GST extracted inclusively, CGST+SGST shown, rounded at the invoice total.
- A rate that follows the goods, not just the department, without forcing HSN capture.
- Runtime-editable rates that survive a Council slab change without a code deploy.
- Tax frozen onto each sale at ring time, so a later rate edit never moves a past bill.

**Non-Goals** (report §10, §4.1)
- e-invoicing / IRP (B2C exempt regardless of turnover).
- HSN capture (`hsn_code` column stays null — not mandatory for B2C < ₹5 cr).
- IGST / inter-state.
- No historical-sale migration (0 sales exist).

## Decisions

### The tax math (inclusive extraction)
The selling price is GST-inclusive, so the invoice **total equals the sum of line prices** — nothing
is added. For each line at rate `r%`: `lineTax = round-free(linePrice × r / (100 + r))`; taxable
value = `linePrice − lineTax`. The **invoice-level** tax is summed then rounded to the nearest rupee
(CGST Act §170); `CGST = SGST = tax / 2`. Rounding at the invoice total (not per line) is the GST
norm and avoids per-line rounding drift. The `taxIsPlaceholder`/+18%-on-top path in `Checkout` is
retired.

*Alternative rejected:* round per line — accumulates paise drift and can disagree with a customer's
hand check of the printed total.

### The rate model — sub_category, effective-dated, resolver
- **`sub_category`** — a controlled reference table (`code`, `name`, parent `category`, timestamps),
  mirroring `category`. It is the rate-bearing classification (e.g. `KITCHEN_METAL` 5%,
  `KITCHEN_PLASTIC` 18%). A controlled vocabulary (not free text) so a rate attaches to a code and
  typos can't fragment it.
- **`product.sub_category`** — nullable FK to that vocabulary.
- **`gst_rate`** — effective-dated table keyed by `sub_category`: `sub_category`, `gst_percent`
  (**DECIMAL**, so 0.25%/3% are exact), `effective_from`, `effective_to`, `is_active`, timestamps.
  `is_active` is the source of truth for "current" (fork **b**); a **partial unique index**
  `WHERE is_active = 1` guarantees exactly one live rate per sub_category even under a bug. A rate
  change is **close-and-open**: flip the old active row off (stamp `effective_to`), insert the new
  active row (`effective_from = today`). Dates are descriptive history.
- **Resolver `GstRates.resolve(subCategory)`** (mirrors `TargetMargins`): the sub_category's active
  rate → else the global **`SETTING` default (18%)**. Two tiers only: because an unclassified item is
  prompted for its sub_category *before the line rings* (below), a category tier would never fire at
  sale time and would be a second source of truth that can drift — so it is deliberately omitted.

### Snapshot onto the sale (immutability)
The resolved numeric rate + derived CGST/SGST/taxable value are **frozen onto `cart_line` at scan
and onto `sale_line` at completion**. A later rate correction can never move tax on a completed sale
(checkout "A completed sale is immutable", report §3.4).

### Classification: backfill + till-prompt
- A one-time **name-heuristic backfill** assigns `sub_category` only when confident (steel/iron/
  aluminium → metal-utensil 5%; plastic/glass/ceramic → 18%; fan/kettle/stove → appliance 18%;
  toys → 5%). Low-confidence items are left **NULL** and flagged — heuristics mis-tag (e.g. a
  "ceramic-coated" *metal* kadhai is 5%, not 18%), and an individually-wrong tag hides in every
  aggregate (see the totals-can't-catch-misdistribution lesson).
- An **unclassified item blocks-and-prompts once at the till** for its sub_category, which then
  persists — correct tax always, matching the existing MRP-at-unpacking gate. It is **not** a hard
  sellability gate; classification can also happen at pricing time.

### Receipt / BillSettings
`ReceiptTemplateService` + `BillSettings` flip from composition to a **Tax Invoice**: title "Tax
Invoice", the composition declaration retired, and a CGST+SGST breakdown printed (taxable value,
CGST, SGST, total). Seed defaults updated by migration.

### Admin
A GST-rate screen (per sub_category, editable, effective-dated, close-and-open on save) + a
sub_category vocabulary editor, mirroring the category-margins admin. Contracts (`CartView`,
`CartLineView`, `SaleView`, `SaleLineView`) gain tax fields; the till shows the incl-GST breakdown.

## Risks / Trade-offs

- **Heuristic mis-tagging** → backfill only auto-assigns high-confidence; the rest stay NULL and are
  caught by the till prompt. No silent wrong tax.
- **Placeholder rates until the CA confirms** → the mechanism ships with a sane default map (18%
  standard, 5% for confirmed-metal/toys); real per-sub_category %s are a **settings edit before
  go-live**, not a code change. Zero live sales, so zero risk of a wrong past bill.
- **Apparel/footwear price-threshold rate** (≤₹2,500 → 5%, above → 18%) → deferred as an auto-rule;
  default those categories' sub_categories to 5% and override the few above-threshold items by hand
  at pricing. The threshold and mechanism are a possible follow-up.
- **Two sources of truth for "current" (`is_active` + dates)** → resolved by making `is_active`
  authoritative and enforcing single-active with the partial unique index; dates are history only.

## Migration Plan

Next migration is **V47+** (main is at V46). Additive, no data backfill of sales (none exist):
`sub_category` table; `product.sub_category`; `gst_rate` table + partial-unique active index; tax
columns on `cart_line` and `sale_line`; updated `bill_settings` seed (Tax Invoice defaults) + seed
of the starting sub_category vocabulary and default rates. Forward-only; reverting the app code
restores prior behavior (the new tables/columns sit unused).

## Open Questions

- The exact per-sub_category **percentages** are the CA's to confirm (report §11.3) — shipped as an
  editable default map, corrected before go-live.
- Whether to later automate the apparel/footwear **price-threshold** rate rather than hand-override.
