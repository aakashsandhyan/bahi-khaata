## 1. Schema and entities (migrations V47+)

- [x] 1.1 Migration: `sub_category` reference table (`code` PK, `name`, `category` FK, timestamps); seed the starting vocabulary (KITCHEN_METAL/KITCHEN_PLASTIC/KITCHEN_APPLIANCE, APPAREL_LOW/HIGH, FOOTWEAR_LOW/HIGH, TOYS, WIRELESS, etc.).
- [x] 1.2 Migration: add nullable `product.sub_category` FK → `sub_category(code)`.
- [x] 1.3 Migration: `gst_rate` table (`id` CHAR(36), `sub_category` FK, `gst_percent` DECIMAL/numeric, `effective_from`, `effective_to` nullable, `is_active` bool, timestamps) + **partial unique index** `ON gst_rate(sub_category) WHERE is_active = 1`; seed a default rate per seeded sub_category (metal/toys 5, most 18) — placeholders, CA-editable.
- [x] 1.4 Migration: add tax snapshot columns to `cart_line` and `sale_line` (`gst_percent`, `tax_paise` — and store enough to render CGST/SGST/taxable; CGST=SGST=tax/2, taxable = price − tax).
- [x] 1.5 Migration: update `bill_settings` seed to Tax Invoice defaults (title "Tax Invoice", retire the composition declaration). Add/confirm a `SETTING` global default GST percent (18).
- [ ] 1.6 Entities: `SubCategory`, `GstRate`; `Product` gains `subCategory`; `CartLine`/`SaleLine` gain the tax snapshot fields. Confirm Hibernate `ddl-auto=validate` passes (CHAR(36)/types match).

## 2. Rate resolver and admin service

- [ ] 2.1 `SubCategoryRepository`, `GstRateRepository` (active row by sub_category).
- [ ] 2.2 `GstRates` resolver (mirror `TargetMargins`): `resolve(subCategory)` → active rate → global `SETTING` default (18%). Return the numeric percent.
- [ ] 2.3 `GstRateService`: set-rate = close-and-open (stamp old `effective_to`+`is_active=false`, insert new active) guarded by the partial-unique index; manage sub_category vocabulary.

## 3. Inclusive tax math + checkout

- [x] 3.1 A `GstMath` helper: `lineTax(price, percent) = price × percent/(100+percent)`; invoice tax = Σ line tax rounded to nearest rupee (§170); CGST = SGST = tax/2; taxable = subtotal − tax.
- [ ] 3.2 `Checkout` (cart view + completion): remove the `taxIsPlaceholder` / +18%-on-top path; `total = subtotal`; resolve each line's rate via `GstRates` (from the product's sub_category); compute + snapshot per-line `gst_percent`/`tax` onto `cart_line`.
- [ ] 3.3 On completion, freeze the per-line tax + the invoice CGST/SGST/taxable onto `sale_line`/`sale` (immutable).
- [ ] 3.4 Till prompt: a scanned product with no `sub_category` blocks the line and prompts once; the choice persists on the product, then the line rings at the resolved rate. Not a hard sellability gate elsewhere.

## 4. Receipt / Tax Invoice

- [ ] 4.1 `ReceiptTemplateService` + `BillSettings`: render a **Tax Invoice** — print taxable value, CGST, SGST, total; drop the composition/no-tax path. Read the tax from the stored sale, never recompute.
- [ ] 4.2 Reprint renders the same snapshotted tax.

## 5. Classification backfill

- [ ] 5.1 One-time name-heuristic backfill assigning `sub_category` **only when confident** (steel/iron/aluminium → metal 5%; plastic/glass/ceramic → 18%; fan/kettle/stove/mixer → appliance 18%; toys → 5%); leave low-confidence NULL and flagged. Reviewable (log/report what it did), not blind.

## 6. Contracts + admin API + frontend

- [ ] 6.1 Contracts: `CartView`/`CartLineView`/`SaleView`/`SaleLineView` gain tax fields (taxable, CGST, SGST, per-line rate/tax); bill-settings view/text.
- [ ] 6.2 API: sub_category CRUD + GST-rate get/set (close-and-open), mirroring the category-margins admin.
- [ ] 6.3 Frontend: till shows the incl-GST breakdown + the unclassified prompt; new sub_category + GST-rate admin screens; catalogue product detail surfaces/edits sub_category.

## 7. Tests and verification

- [ ] 7.1 `GstMath` unit tests: ₹510 @18% → tax ≈ ₹77.80, total stays ₹510; invoice-level rounding; CGST=SGST; a 5% line; a 0.25% decimal rate.
- [ ] 7.2 Resolver tests: sub_category active rate wins; unclassified → 18% default; close-and-open leaves exactly one active row (partial-index enforced).
- [ ] 7.3 Checkout tests: total not inflated; per-line tax snapshotted; completion freezes CGST/SGST/taxable; a later rate change does not move a past sale; unclassified item prompts before ringing.
- [ ] 7.4 Receipt test: Tax Invoice prints the snapshotted CGST/SGST/taxable; reprint identical.
- [ ] 7.5 Migration test: fresh DB reaches V47+ gapless; partial-unique index rejects a second active rate; backfill assigns confident items and leaves the rest NULL.
- [ ] 7.6 Run `./gradlew :backend:test` + `npm run build`; confirm app boots (schema validation). Frontend tsc gate.
