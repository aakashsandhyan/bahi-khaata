## 1. Foundation

- [x] 1.1 Spike the Hibernate/SQLite dialect before anything is built on it. In a throwaway module, prove that `hibernate-community-dialects` handles: a JSON column mapped through `@JdbcTypeCode(SqlTypes.JSON)`, an integer column holding paise, an `@Immutable` entity that dirty-checking never updates, and startup with Flyway applying migrations followed by `ddl-auto=validate` passing. **This task is a gate** — if it fails, the ORM decision in design §3 reopens before any schema work exists to throw away.
- [x] 1.2 Create the Gradle multi-project build: root build plus `contracts`, `backend`, `terminal`, `dashboard`. Declare only these dependency edges — `terminal → contracts`, `backend → contracts`, `dashboard → contracts`. `terminal → backend` must not exist.
- [x] 1.3 Add ArchUnit tests to the root build that fail on any package dependency crossing a boundary not declared in 1.2, and on any JPA, Spring, or persistence annotation appearing in `contracts`. Verify the tests actually fail by temporarily introducing a violation.
- [x] 1.4 Stand up the Spring Boot application in `backend` with a SQLite datasource, and configure `ddl-auto=validate` in every profile. Confirm no profile sets `update` or `create-drop`.
- [x] 1.5 Wire Flyway with plain SQL migrations under `backend`, applied at startup and tracked in its history table. Add a baseline migration and confirm a clean database reaches a known version unattended.
- [x] 1.6 Implement the money value type over integer paise, with parsing, formatting, arithmetic, and rupee rounding half-up per CGST §170. Cover it with tests including the rounding boundary at exactly 50 paise. No `double` or `float` anywhere in the type or its callers.
- [x] 1.7 Establish the UUIDv4 primary key convention as a shared mapping used by every entity, stored as 36-character TEXT.
- [x] 1.8 Add a SQL migration helper or documented pattern for the `UPDATE`/`DELETE`-rejecting triggers, so the immutability triggers in later sections are written one way rather than reinvented per table. Confirm a trigger fires against a direct `sqlite3` session, not just through the application.
- [x] 1.9 Add a health endpoint to `backend` and its DTO to `contracts`.
- [x] 1.10 Create the JavaFX application skeleton in `terminal` with its HTTP client pointed at the backend base URL, and confirm it can call the health endpoint end to end. The waiting-state behaviour itself belongs to the `sales-checkout` section.
- [x] 1.11 Add AGPL-3.0: full licence text at the repository root, the copyright header pattern applied to existing sources, and the licence declared in the Gradle build. Do this before the codebase grows, so headers are established rather than retrofitted across hundreds of files.
- [x] 1.12 Add the `SETTING` key-value table via migration, seeded with the margin review threshold, target margin, and cart expiry defaults, plus a typed accessor for reading them. Business parameters only — infrastructure configuration stays in properties.
- [x] 1.13 Write the developer setup path — clone, build, run backend, run terminal — and verify it works from a clean checkout on a machine that has never built this project. Any manual database step here is a defect.

## 2. Products and barcodes

- [x] 2.1 Write the migration creating `PRODUCT` (UUID text PK, name, `category` with `CHECK (category IN (...))` over the six category values, nullable `selling_price_paise`, `hsn_code`, JSON `attributes` declared CLOB, `price_review_flagged`) and `BARCODE` (UUID text PK, product FK, `code` with a unique constraint, `origin`). Index `BARCODE.code` — it is the checkout hot path. Review this SQL before any entity class exists.
- [x] 2.2 Add the `Category` Java enum — `HOME_ESSENTIALS`, `KITCHEN`, `ELECTRONICS`, `GIFTING`, `DECOR`, `FASHION` — and a test asserting the enum values and the migration's CHECK list are the same set, so the two cannot drift.
- [x] 2.3 Map the `Product` entity and repository, with `category` as the `Category` enum stored as its name, and `attributes` mapped through `@JdbcTypeCode(SqlTypes.JSON)` using the approach proven in 1.1.
- [x] 2.4 Test that category attributes round-trip unchanged, that a product with no attributes is valid, and that a product carrying attribute names never previously used stores without a migration.
- [x] 2.5 Map the `Barcode` entity with its uniqueness constraint, and test that assigning a code already held by another product is refused.
- [x] 2.6 Implement barcode resolution: a known code returns its product, an unknown code is reported as unknown and creates nothing. Test both, including that no row is written on the unknown path.
- [x] 2.7 Implement internal barcode value generation with the reserved prefix `BBZ-` and a six-digit counter starting at 100000 (`BBZ-100000` … `BBZ-999999`). Test that generated codes carry the prefix, that successive codes differ, that origin distinguishes internal from manufacturer codes, and that exhaustion past six digits fails loudly. The prefix is letters, so a generated code can never equal an all-numeric EAN-13 — that is the collision guarantee. ZXing rendering the value to a Code 128 label *image* is deferred to labelling (a future change; printing is out of scope here).
- [x] 2.8 Implement the unpriced state: `selling_price_paise` nullable, an explicit way to ask whether a product is priced, and a guarantee that an absent price is never returned or coerced to zero. Test that an unpriced product reports as unpriced rather than as costing nothing.
- [x] 2.9 Implement explicit price setting, and test that the price is unchanged by every other product operation. This is the invariant the whole pricing model rests on, so test it directly rather than trusting it.
- [x] 2.10 Add product and barcode-lookup DTOs to `contracts`, carrying money as integer paise and representing an absent price as absent rather than zero.
- [x] 2.11 Add backend endpoints for barcode lookup, product creation, and price setting, wired to the DTOs from 2.10. No terminal UI in this section — products are created through goods-in, which arrives in section 4.

## 3. Lots and cost allocation

- [x] 3.1 Write the migration creating `LOT` (UUID text PK, supplier, `received_on`, `amount_paid_paise`, `freight_paise`, `allocation_method`) and `BATCH` (UUID text PK, product FK, lot FK, `allocated_unit_cost_paise`, `cost_basis`, `quantity_received`, `quantity_damaged`, `mrp_paise`, `mrp_is_estimate`, `received_at`). Review this SQL before any entity class exists.
- [x] 3.2 Map the `Lot` and `Batch` entities and repositories.
- [ ] 3.3 Implement the allocation calculation as a pure function over plain inputs — lot amount, freight, and lines of quantity, MRP, optional pinned cost, and damaged quantity — returning allocated costs. No entities, no repositories, no Spring. Everything below tests this function directly, which is what makes the arithmetic reviewable in isolation from persistence.
- [ ] 3.4 Implement proportional allocation by quantity × MRP, and test that lines of differing value receive proportionally different costs. Include the mixed-pallet case where a naive equal split would price a low-value item above its retail price.
- [ ] 3.5 Implement pinning: pinned lines take their stated cost and their total is deducted from the lot amount *before* the remainder is spread across unpinned lines. Test that pinning one line changes what the other lines receive — not merely what the pinned line receives.
- [ ] 3.6 Reject a pinned total exceeding the lot amount, reporting the excess. Accept a fully pinned lot only when the pinned total equals the lot amount exactly.
- [ ] 3.7 Implement exact reconciliation in integer paise, assigning any division remainder to the largest line. Cover this with a property-based test asserting that allocated costs always sum to the lot amount, across many generated line combinations — a worked example or two will not find the rounding cases that matter here.
- [ ] 3.8 Implement damaged-unit handling: damaged quantity is recorded, excluded from the divisor so its cost is absorbed by the sellable units, and excluded from quantity on hand. Test that the lot remains fully allocated when units are damaged.
- [ ] 3.9 Record provenance — `cost_basis` on each batch as allocated, pinned, or imported, and `allocation_method` on the lot. Test that each route sets the basis it should.
- [ ] 3.10 Add freight to the amount allocated, and test that a lot with freight spreads the larger total.
- [ ] 3.11 Add lot and lot-line DTOs to `contracts`, and a backend endpoint creating a lot with its lines and resulting batches. Reject a lot that fails allocation rather than persisting a partial result.
- [ ] 3.12 Test that changing the configured allocation method leaves previously allocated lots untouched, and that a lot allocated afterwards uses and records the new method.

## 4. Stock ledger and FIFO consumption

- [x] 4.1 Write the migration creating `STOCK_LEDGER` (UUID text PK, product FK, batch FK, nullable invoice FK, signed `quantity`, `movement_type`, nullable `cogs_paise`, `effective_at`), with `UPDATE`- and `DELETE`-rejecting triggers using the pattern from 1.8. Review this SQL before any entity class exists.
- [x] 4.2 Verify the triggers reject `UPDATE` and `DELETE` from a direct `sqlite3` session, not only through the application. The spec requires database-level enforcement; an application-only guard does not satisfy it.
- [x] 4.3 Map the ledger entity as `@Immutable` with no setters, constructed complete. Test that Hibernate's dirty checking never emits an `UPDATE` for it — this is the specific hazard JPA introduces here.
- [x] 4.4 Implement movement types and signed quantities. Test that a receipt is positive, a sale is negative, and an adjustment may be either, with no separate direction field able to contradict the sign.
- [ ] 4.5 Wire lot creation from 3.11 to append receipt movements for each batch, in the same transaction that creates the lot.
- [x] 4.6 Implement quantity on hand derived from the ledger, per product and per batch. Test that it equals the net of appended movements and that damaged units are excluded.
- [x] 4.7 Implement FIFO consumption: draw from the oldest batch with quantity remaining, moving to the next when exhausted, recording how much came from each. Test the spanning case explicitly.
- [x] 4.8 Attribute cost of goods sold at the consumed batch's cost, splitting across batches when a consumption spans them. Test that a two-batch consumption records each portion at its own cost.
- [x] 4.9 Implement backdated movements: appended at their effective date with derived figures recalculated forward, and no existing ledger row modified. Test that a late-logged receipt changes on-hand from its effective date onward while leaving prior rows byte-identical.
- [x] 4.10 Implement point-in-time valuation and on-hand as at a given date, excluding movements effective after it.
- [x] 4.11 Implement freeze-on-consumption: a lot and its batches are editable while no stock from the lot has been consumed and refused thereafter, with edits before the freeze triggering reallocation. Test that consuming from one batch freezes the entire lot, not just that batch. (The policy and its guard are built; the edit operations it guards, and the reallocation an edit triggers, arrive with the parked cost allocator.)
- [x] 4.12 Add stock query DTOs to `contracts` and the backend endpoints behind them.
- [ ] 4.13 Build the goods-in screen in `terminal`: create a lot, add lines with quantity and MRP, optionally pin a cost, mark damaged units, see the resulting allocation, and submit. Deliberately rough — the allocation figures being visible and correct is the goal, not the layout.

## 5. Pricing and margin review

- [ ] 5.1 Capture MRP per batch at receipt, including the estimated-value path for goods with no printed MRP, with estimates distinguishable from printed values.
- [ ] 5.2 Expose the most recently received batch's MRP as the product's MRP for display, and test that earlier batches retain their own.
- [ ] 5.3 Implement gross margin as (price − cost) ÷ price. Name the method so it cannot be mistaken for markup, and test a case where the two diverge sharply — this is a standard retail arithmetic bug and the test is what stops it recurring.
- [ ] 5.4 Add the `category_margin` table via migration — one row per `Category`, holding a target percent, runtime-editable — and a typed accessor. Update the `pricing.target_margin_percent` SETTING description to name it the global fallback (this is where the V2 wording is corrected, in a new migration rather than by editing V2).
- [ ] 5.5 Implement the three-tier target-margin resolution: transient custom (passed in, never stored) → `category_margin` row for the product's category → global SETTING default. Test each tier wins over the next, and that the custom value is not persisted.
- [ ] 5.6 Implement the suggested selling price from allocated unit cost and the resolved target margin. Offered only for unpriced products.
- [ ] 5.7 Test that a suggestion never becomes a price on its own, that the product stays unpriced and unsellable until someone accepts, and that an override is honoured.
- [ ] 5.8 Test that a product with an existing price receives no suggestion and its price is untouched when new stock arrives at any cost.
- [ ] 5.9 Implement margin-erosion flagging at receipt: flag when gross margin drops by the `SETTING` threshold or more against the margin at the most recent prior batch cost.
- [ ] 5.10 Test the no-flag cases: first batch of a product, a cheaper batch, erosion below threshold, and an unpriced product.
- [ ] 5.11 Clear the flag when a price is explicitly set, and test that a flagged product continues to sell at its existing price with the flag intact. Flagging must never block a sale.
- [ ] 5.12 Surface the suggestion and the flag in the goods-in screen from 4.13, so a price can be set during intake.

## 6. Invoicing

- [ ] 6.1 Add `stock_ledger.invoice_id` via `ALTER TABLE ... ADD COLUMN invoice_id CHAR(36) REFERENCES invoice (id)` — deferred from 4.1 because the invoice table did not exist yet; SQLite permits this for a nullable column and it leaves the append-only triggers intact. Then write the migration creating `INVOICE` (UUID text PK, unique `number`, `financial_year`, nullable self-referencing `corrects_invoice_id`, `issued_at`, `place_of_supply`, supplier identity fields, `total_unrounded_paise`, `total_rounded_paise`, nullable `irn`, signed QR and acknowledgement fields) and `INVOICE_LINE`, with `UPDATE`/`DELETE`-rejecting triggers on issued rows. Review this SQL before any entity class exists.
- [ ] 6.2 Verify at the database level that an issued invoice and its lines reject `UPDATE` and `DELETE` from a direct `sqlite3` session, while an unissued invoice remains editable.
- [ ] 6.3 Map the invoice and line entities as `@Immutable` once issued, and test that dirty checking cannot emit an `UPDATE` against an issued invoice.
- [ ] 6.4 Implement the mandatory-field check and refuse to issue an invoice missing any of them, reporting which field is missing rather than failing opaquely.
- [ ] 6.5 Require recipient details when the invoice total exceeds ₹50,000, and test both sides of the threshold.
- [ ] 6.6 Implement the place-of-supply tax split — CGST and SGST within the supplier's own state, IGST otherwise. Structure only; the HSN-to-rate mapping stays blocked on the CA consultation and must not be guessed.
- [ ] 6.7 Implement invoice numbering: consecutive without gaps within a financial year, allocated inside the same transaction that issues the invoice, at most 16 characters using only alphanumerics, hyphens and slashes.
- [ ] 6.8 Store the financial year explicitly on the invoice and reset numbering at 1 April. Test the boundary directly — a financial year derived at query time is how April produces a duplicate number.
- [ ] 6.9 Test that a failed issue consumes no number: force a failure after allocation and confirm the next successful invoice takes that number, leaving no gap.
- [ ] 6.10 Implement rounding once at issue, half-up per CGST §170, persisting both the unrounded and rounded totals. Test that the rounded total derives from the unrounded sum of lines rather than from individually rounded lines.
- [ ] 6.11 Implement corrections as new entries referencing the original, and test that the original remains present and unchanged afterwards.
- [ ] 6.12 Confirm the IRN, signed QR and acknowledgement fields are present and empty, and that issuing an invoice makes no outbound request to any external portal.

## 7. Checkout

- [ ] 7.1 Write the migration creating `CART` and `CART_LINE`. These are mutable working state — confirm the immutability triggers do not apply to them.
- [ ] 7.2 Map the cart entities and persist an open cart in the backend, with the terminal holding no local copy as source of truth.
- [ ] 7.3 Implement scanning: resolve a code, add a line at the product's current selling price, and increment quantity when the same code is scanned again rather than adding a duplicate line.
- [ ] 7.4 Report an unrecognised code without disturbing lines already on the cart.
- [ ] 7.5 Refuse an unpriced product with a clear message, adding no line. Test that it is never added at zero.
- [ ] 7.6 Implement line removal and the running total.
- [ ] 7.7 Implement cart recovery: restart the terminal mid-sale and confirm the cart returns with its lines intact. Test the interruption case by killing the terminal process, not by a clean shutdown.
- [ ] 7.8 Implement explicit void, discarding the cart with no invoice and no stock consumed.
- [ ] 7.9 Add the end-of-day close operation: a manual backend endpoint that discards all open carts, and warns first — returning the list of open carts so the terminal can show them before the operator confirms. A cart open at close is usually abandoned but occasionally a forgotten sale, so the warning must distinguish an open-cart close from a clean one. Carts persist through the trading day; nothing but close, void, finalisation, or the backstop clears them.
- [ ] 7.10 Replace the obsolete `checkout.cart_expiry_minutes` SETTING with `checkout.cart_stale_hours` (default 24) via a new migration — not by editing the applied V2 — and update `Settings` to expose the staleness window rather than the old inactivity duration. Implement the backstop as a scheduled backend job that discards any cart untouched beyond the staleness window, even if the day was never closed. Coarse by design; make its failures visible in logs. This is the first unattended job in the system.
- [ ] 7.11 Implement finalisation as a single transaction issuing the invoice, appending ledger rows, and consuming stock FIFO. Test that invoice quantities reconcile with ledger quantities, remembering that one line at quantity two may produce two ledger rows.
- [ ] 7.12 Test that a failure during finalisation leaves no invoice, no ledger row, and unchanged quantity on hand. Force the failure at more than one point in the transaction.
- [ ] 7.13 Refuse finalisation when a line exceeds quantity on hand, reporting which product is short. Test the exactly-sufficient case succeeds and leaves on-hand at zero.
- [ ] 7.14 Discard the cart on successful finalisation, leaving the invoice and ledger rows as the record.
- [ ] 7.15 Implement the terminal's waiting state: poll health on startup, show a plain-language message while the backend is unreachable, present checkout once it answers without requiring a restart, and surface a mid-sale loss of the backend without reporting the sale as finalised.
- [ ] 7.16 Build the checkout screen in `terminal` — scan field, line list, running total, finalise, and an on-screen invoice view. Rough is fine; correct is not optional.

## 8. Packaging and operations

- [ ] 8.1 Write the backend Dockerfile and Compose file so a self-hosting retailer starts it with one command.
- [ ] 8.2 Package the terminal with `jpackage`, bundling a trimmed JRE, and confirm the installer works on a machine with no JDK installed.
- [ ] 8.3 Register the backend to start at boot, so the terminal's health polling is a safety net rather than the primary mechanism.
- [ ] 8.4 Provision the database in WAL mode: the installer pre-creates `data/bahi-khaata.db` and runs a one-shot `PRAGMA journal_mode = WAL` before the backend first starts. WAL is persistent, so this is once-only — not app startup, not connection-init. See design decision 4a. Confirm the provisioned database reports `wal` from a direct `sqlite3` session.
- [ ] 8.5 Automate nightly database backups to a destination that survives loss of the counter PC. Local disk alone does not satisfy this.
- [ ] 8.6 Rehearse the restore. Actually destroy a database and bring it back from a backup, then write down what you did. Confirm the restored database is still in WAL mode — a `.backup` or `VACUUM INTO` restore can reset journal mode, silently dropping crash resilience. Migrations are forward-only, so this procedure is the entire rollback story and an unrehearsed one is not a mitigation.
- [ ] 8.7 Run the whole slice by hand end to end — receive a lot, price a product, scan it, finalise, inspect the invoice and ledger — and record every model gap it surfaces. Fold those gaps into the schema before this change closes, while no real transaction data exists.
- [ ] 8.8 Write the README: what the project is, how to self-host it, the AGPL terms, and the architecture in brief. (A first pass was written in task 1.13; extend it for the self-host path once the Docker and installer work above exists.)
