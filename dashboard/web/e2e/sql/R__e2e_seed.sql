-- R__e2e_seed.sql — deterministic fixture data for the Playwright smoke suite.
--
-- Repeatable migration: Flyway re-applies it (after every versioned migration) whenever its
-- checksum changes, and the harness deletes the scratch database before every run anyway, so
-- this always lands on a freshly migrated, empty schema. Every id, barcode, and timestamp here
-- is a fixed literal (see dashboard/web/e2e/seed.ts for the mirrored TypeScript constants) so
-- two runs produce byte-equivalent seed state.
--
-- Categories (KITCHEN, ELECTRONICS) and their target margins already exist from the baseline
-- migrations (V8/V10) — nothing to insert there. The printer_config row is the one place this
-- file has to be trusted absolutely: enabled = 0 and an address on the TEST-NET-3 unroutable
-- block (203.0.113.1, RFC 5737), so no smoke test can ever reach a physical printer even if it
-- exercises the print-queue endpoints.

-- --- supplier ---------------------------------------------------------------------------------
INSERT INTO supplier (id, name, name_normalized, gstin, phone, address, contact_person, notes, active, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-000000000001',
  'E2E Test Supplier',
  'e2e test supplier',
  NULL, '9990001234', 'Bhopal test address', 'Seed Contact', 'Seeded for the Playwright smoke suite',
  1,
  '2026-08-01T08:00:00.000Z', '2026-08-01T08:00:00.000Z'
);

-- --- lot --------------------------------------------------------------------------------------
-- OPEN, so it is picked up by every screen that lists only open lots (Receiving, Lots, Pricing).
INSERT INTO lot (id, supplier, received_on, amount_paid_paise, freight_paise, allocation_method, created_at, updated_at, state, closed_at, receiving_complete, is_manual, supplier_id)
VALUES (
  'e2e00001-0000-4000-8000-000000000002',
  'E2E Test Supplier',
  '2026-08-01',
  500000, 0, 'FULLY_PINNED',
  '2026-08-01T08:05:00.000Z', '2026-08-01T08:05:00.000Z',
  'OPEN', NULL, 0, 0,
  'e2e00001-0000-4000-8000-000000000001'
);

-- --- box_receipt (Receiving screen) — two different states -----------------------------------
INSERT INTO box_receipt (id, lot_id, manifest_carton_id, box_state, received_at, rejected_reason, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-000000000003',
  'e2e00001-0000-4000-8000-000000000002',
  'E2E-BOX-001', 'RECEIVED', '2026-08-01T09:00:00.000Z', NULL,
  '2026-08-01T08:10:00.000Z', '2026-08-01T09:00:00.000Z'
);

INSERT INTO box_receipt (id, lot_id, manifest_carton_id, box_state, received_at, rejected_reason, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-000000000004',
  'e2e00001-0000-4000-8000-000000000002',
  'E2E-BOX-002', 'EXPECTED', NULL, NULL,
  '2026-08-01T08:10:00.000Z', '2026-08-01T08:10:00.000Z'
);

-- --- box (Unpacking screen) — the physical carton scanned at the counter ----------------------
INSERT INTO box (id, lot_id, tracking_number, finished_at, created_at, updated_at, last_activity_at)
VALUES (
  'e2e00001-0000-4000-8000-000000000005',
  'e2e00001-0000-4000-8000-000000000002',
  'E2E-BOX-001', NULL,
  '2026-08-01T08:10:00.000Z', '2026-08-01T08:10:00.000Z', NULL
);

-- --- products across the lifecycle -------------------------------------------------------------
-- (a) priced, GOOD, barcoded, on the till and the cart.
INSERT INTO product (id, name, category, selling_price_paise, hsn_code, attributes, price_review_flagged, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-00000000000a',
  'E2E Priced Kettle', 'KITCHEN', 49900, NULL, NULL, 0,
  '2026-08-01T08:15:00.000Z', '2026-08-01T08:15:00.000Z'
);

-- (b) counted into a batch, no selling price yet — the pricing workbench's target.
INSERT INTO product (id, name, category, selling_price_paise, hsn_code, attributes, price_review_flagged, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-00000000000b',
  'E2E Counted Toaster', 'KITCHEN', NULL, NULL, NULL, 0,
  '2026-08-01T08:16:00.000Z', '2026-08-01T08:16:00.000Z'
);

-- (c) needs-work — sits in the Prep backlog waiting on CLEAN.
INSERT INTO product (id, name, category, selling_price_paise, hsn_code, attributes, price_review_flagged, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-00000000000c',
  'E2E Needs Work Blender', 'KITCHEN', NULL, NULL, NULL, 0,
  '2026-08-01T08:17:00.000Z', '2026-08-01T08:17:00.000Z'
);

-- (d) damaged — sits in the Prep "review damaged" pile.
INSERT INTO product (id, name, category, selling_price_paise, hsn_code, attributes, price_review_flagged, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-00000000000d',
  'E2E Damaged Mixer', 'ELECTRONICS', NULL, NULL, NULL, 0,
  '2026-08-01T08:18:00.000Z', '2026-08-01T08:18:00.000Z'
);

-- (e) still only on the manifest — nobody has scanned it yet. Its expected_line below is what the
-- Unpacking screen shows when the seeded box is opened.
INSERT INTO product (id, name, category, selling_price_paise, hsn_code, attributes, price_review_flagged, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-00000000000e',
  'E2E Unpacking Widget', 'KITCHEN', NULL, NULL, NULL, 0,
  '2026-08-01T08:19:00.000Z', '2026-08-01T08:19:00.000Z'
);

-- --- barcodes -----------------------------------------------------------------------------------
INSERT INTO barcode (id, product_id, code, origin, created_at)
VALUES (
  'e2e00001-0000-4000-8000-00000000001a',
  'e2e00001-0000-4000-8000-00000000000a',
  'E2E0000000001', 'MANUFACTURER', '2026-08-01T08:15:00.000Z'
);

INSERT INTO barcode (id, product_id, code, origin, created_at)
VALUES (
  'e2e00001-0000-4000-8000-00000000001b',
  'e2e00001-0000-4000-8000-00000000000b',
  'E2E0000000002', 'MANUFACTURER', '2026-08-01T08:16:00.000Z'
);

-- Product A also carries its shelf barcode (BBZ), same as every product actually priced through
-- ShelfPricing.saveExisting mints one on save. Reprint (and any bulk-label screen) resolves and
-- displays THIS code, never the manufacturer one, so it has to exist for those screens to work.
INSERT INTO barcode (id, product_id, code, origin, created_at)
VALUES (
  'e2e00001-0000-4000-8000-00000000001c',
  'e2e00001-0000-4000-8000-00000000000a',
  'BBZ-100000', 'INTERNAL', '2026-08-01T08:15:00.000Z'
);

-- Reserve BBZ-100000 so InternalBarcodeGenerator's next real allocation (used when a test prices
-- the other seeded products through the UI) starts at BBZ-100001 and can never collide with it.
UPDATE internal_barcode_counter SET last_seq = 100000 WHERE id = 1;

-- --- batches --------------------------------------------------------------------------------
-- (a) GOOD, costed and priced.
INSERT INTO batch (id, product_id, lot_id, condition, issue_type, allocated_total_paise, allocated_unit_cost_paise, cost_basis, quantity_received, quantity_damaged, mrp_paise, mrp_is_estimate, labelled_at, created_at, updated_at, remark)
VALUES (
  'e2e00001-0000-4000-8000-00000000002a',
  'e2e00001-0000-4000-8000-00000000000a',
  'e2e00001-0000-4000-8000-000000000002',
  'GOOD', NULL, 200000, 20000, 'PINNED', 10, 0, 59900, 0, NULL,
  '2026-08-01T08:20:00.000Z', '2026-08-01T08:20:00.000Z', NULL
);

-- (b) GOOD, costed, not yet priced.
INSERT INTO batch (id, product_id, lot_id, condition, issue_type, allocated_total_paise, allocated_unit_cost_paise, cost_basis, quantity_received, quantity_damaged, mrp_paise, mrp_is_estimate, labelled_at, created_at, updated_at, remark)
VALUES (
  'e2e00001-0000-4000-8000-00000000002b',
  'e2e00001-0000-4000-8000-00000000000b',
  'e2e00001-0000-4000-8000-000000000002',
  'GOOD', NULL, 50000, 10000, 'PINNED', 5, 0, 29900, 0, NULL,
  '2026-08-01T08:21:00.000Z', '2026-08-01T08:21:00.000Z', NULL
);

-- (c) NEEDS_WORK — off-ledger, waiting on the CLEAN department.
INSERT INTO batch (id, product_id, lot_id, condition, issue_type, allocated_total_paise, allocated_unit_cost_paise, cost_basis, quantity_received, quantity_damaged, mrp_paise, mrp_is_estimate, labelled_at, created_at, updated_at, remark)
VALUES (
  'e2e00001-0000-4000-8000-00000000002c',
  'e2e00001-0000-4000-8000-00000000000c',
  'e2e00001-0000-4000-8000-000000000002',
  'NEEDS_WORK', 'CLEAN', NULL, NULL, NULL, 3, 0, NULL, 0, NULL,
  '2026-08-01T08:22:00.000Z', '2026-08-01T08:22:00.000Z', NULL
);

-- (d) DAMAGED — sellable as seconds, review-listed with a remark.
INSERT INTO batch (id, product_id, lot_id, condition, issue_type, allocated_total_paise, allocated_unit_cost_paise, cost_basis, quantity_received, quantity_damaged, mrp_paise, mrp_is_estimate, labelled_at, created_at, updated_at, remark)
VALUES (
  'e2e00001-0000-4000-8000-00000000002d',
  'e2e00001-0000-4000-8000-00000000000d',
  'e2e00001-0000-4000-8000-000000000002',
  'DAMAGED', NULL, NULL, NULL, NULL, 2, 0, NULL, 0, NULL,
  '2026-08-01T08:23:00.000Z', '2026-08-01T08:23:00.000Z', 'Dented body, scanned at unpacking'
);

-- --- stock ledger — the stock-bearing batches (GOOD, DAMAGED) get a receipt -------------------
INSERT INTO stock_ledger (id, product_id, batch_id, quantity, movement_type, cogs_paise, effective_at, created_at)
VALUES (
  'e2e00001-0000-4000-8000-00000000003a',
  'e2e00001-0000-4000-8000-00000000000a',
  'e2e00001-0000-4000-8000-00000000002a',
  10, 'PURCHASE_RECEIPT', NULL, '2026-08-01T08:20:00.000Z', '2026-08-01T08:20:00.000Z'
);

INSERT INTO stock_ledger (id, product_id, batch_id, quantity, movement_type, cogs_paise, effective_at, created_at)
VALUES (
  'e2e00001-0000-4000-8000-00000000003b',
  'e2e00001-0000-4000-8000-00000000000b',
  'e2e00001-0000-4000-8000-00000000002b',
  5, 'PURCHASE_RECEIPT', NULL, '2026-08-01T08:21:00.000Z', '2026-08-01T08:21:00.000Z'
);

INSERT INTO stock_ledger (id, product_id, batch_id, quantity, movement_type, cogs_paise, effective_at, created_at)
VALUES (
  'e2e00001-0000-4000-8000-00000000003d',
  'e2e00001-0000-4000-8000-00000000000d',
  'e2e00001-0000-4000-8000-00000000002d',
  2, 'PURCHASE_RECEIPT', NULL, '2026-08-01T08:23:00.000Z', '2026-08-01T08:23:00.000Z'
);

-- --- expected_line — what the Unpacking screen shows when the seeded box is opened ------------
INSERT INTO expected_line (id, lot_id, box_id, product_id, code, quantity_expected, stated_value_paise, created_at, updated_at, quantity_counted)
VALUES (
  'e2e00001-0000-4000-8000-000000000041',
  'e2e00001-0000-4000-8000-000000000002',
  'e2e00001-0000-4000-8000-000000000005',
  'e2e00001-0000-4000-8000-00000000000e',
  'E2E-UNPACK-001', 5, NULL,
  '2026-08-01T08:11:00.000Z', '2026-08-01T08:11:00.000Z', 0
);

-- --- cart + cart line — an open sale sitting on the priced product -----------------------------
INSERT INTO cart (id, state, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-000000000051',
  'OPEN', '2026-08-01T09:30:00.000Z', '2026-08-01T09:30:00.000Z'
);

INSERT INTO cart_line (id, cart_id, product_id, unit_price_paise, mrp_paise, quantity, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-000000000052',
  'e2e00001-0000-4000-8000-000000000051',
  'e2e00001-0000-4000-8000-00000000000a',
  49900, 59900, 1,
  '2026-08-01T09:30:00.000Z', '2026-08-01T09:30:00.000Z'
);

-- --- a completed sale ---------------------------------------------------------------------------
-- A second, PAID cart plus the SALE ledger entry it produced. The dashboard has no "pay" flow yet
-- (Checkout.tsx's own comment: "till does not take money yet"), so nothing exercises this through
-- the UI today — it exists as fixture data for the OpenSpec spec's "one completed sale" lifecycle
-- state, ready for whichever screen reads sale history first.
INSERT INTO cart (id, state, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-000000000053',
  'PAID', '2026-08-01T09:45:00.000Z', '2026-08-01T09:46:00.000Z'
);

INSERT INTO cart_line (id, cart_id, product_id, unit_price_paise, mrp_paise, quantity, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-000000000054',
  'e2e00001-0000-4000-8000-000000000053',
  'e2e00001-0000-4000-8000-00000000000a',
  49900, 59900, 1,
  '2026-08-01T09:45:00.000Z', '2026-08-01T09:45:00.000Z'
);

INSERT INTO stock_ledger (id, product_id, batch_id, quantity, movement_type, cogs_paise, effective_at, created_at)
VALUES (
  'e2e00001-0000-4000-8000-00000000003e',
  'e2e00001-0000-4000-8000-00000000000a',
  'e2e00001-0000-4000-8000-00000000002a',
  -1, 'SALE', 20000, '2026-08-01T09:46:00.000Z', '2026-08-01T09:46:00.000Z'
);

-- --- product capture — a phone submission waiting in the review queue ---------------------------
INSERT INTO product_capture (id, name, mrp_paise, description, lot_id, status, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-000000000061',
  'E2E Captured Sample', 15000, 'Seeded for the review queue smoke test', NULL, 'pending',
  '2026-08-01T09:00:00.000Z', '2026-08-01T09:00:00.000Z'
);

-- --- print job — a label held in the review queue, never in 'queued' state ---------------------
-- Status 'review' is deliberate: the print poller (@Scheduled, every 500ms) only ever picks up
-- rows in status 'queued', so this row is never sent anywhere by the backend on its own.
INSERT INTO print_job (id, barcode, product_name, selling_price_paise, mrp_paise, copies, product_id, status, error, retry_count, created_at, updated_at, operator_name)
VALUES (
  'e2e00001-0000-4000-8000-000000000071',
  'E2E0000000001', 'E2E Priced Kettle', 49900, 59900, 2,
  'e2e00001-0000-4000-8000-00000000000a',
  'review', NULL, 0,
  '2026-08-01T09:35:00.000Z', '2026-08-01T09:35:00.000Z', 'E2E Seed'
);

-- --- printer config — the one row that must never be able to reach a real printer --------------
-- id is PrinterConfig.SINGLETON_ID exactly (00000000-0000-0000-0000-000000000001) — the repository
-- looks up that fixed id, not "whichever row exists", so any other id is silently invisible and the
-- app falls back to its own hardcoded default (a routable-looking placeholder address). Getting
-- this id right is what makes the rest of this row's safety properties take effect at all.
-- enabled = 0: UsbPrinterDriver.sendLabel throws "Printer is disabled" before opening any socket.
-- address is 203.0.113.1 (RFC 5737 TEST-NET-3, guaranteed unroutable) as defence in depth even if
-- the enabled check were ever bypassed.
INSERT INTO printer_config (id, address, port_speed, paper_size, copies_default, enabled, test_status, test_error, last_tested_at, created_at, updated_at)
VALUES (
  '00000000-0000-0000-0000-000000000001',
  '203.0.113.1:9100', 9600, '4x6', 1, 0, NULL, NULL, NULL,
  '2026-08-01T08:00:00.000Z', '2026-08-01T08:00:00.000Z'
);

-- --- receipt printer (V43) — same two-layer safety as the label printer -------------------------
-- V43 seeds this singleton disabled with an empty address; pin it to the TEST-NET-3 block anyway
-- so even an accidentally-enabled test run has nowhere real to connect.
UPDATE receipt_printer_config
SET address = '203.0.113.1:9101', enabled = 0
WHERE id = '00000000-0000-0000-0000-000000000002';

-- --- sales (V42) — two completed bills, for the Dashboard's revenue-today tile and Sales screen --
-- created_at/updated_at MUST be strftime('%Y-%m-%dT%H:%M:%S.000Z','now'), not a fixed literal and
-- not raw datetime('now'): this is a repeatable migration, re-applied every run against a freshly
-- recreated database, so a fixed literal would fall out of "today" the day after it was written —
-- and datetime('now') is space-separated with no 'Z' and no milliseconds, a shape that would never
-- match the app's ISO-8601-Z strings, so the dashboard's string-range window would silently miss it
-- and the revenue tile would sit at ₹0 with nothing but the spec's non-zero assertion to catch it
-- (see design.md D10). Both bills sell the priced kettle (product a) at its seeded price/MRP, so
-- their figures are computable by hand from constants already in this file.
INSERT INTO sale (id, bill_no, payment_method, subtotal_paise, saving_paise, tax_paise, total_paise, operator_name, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-000000000081',
  9001, 'CASH', 99800, 20000, 0, 99800, 'E2E Seed',
  strftime('%Y-%m-%dT%H:%M:%S.000Z','now'), strftime('%Y-%m-%dT%H:%M:%S.000Z','now')
);

INSERT INTO sale_line (id, sale_id, product_id, name, barcode, mrp_paise, unit_price_paise, quantity, line_total_paise, saving_paise, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-000000000091',
  'e2e00001-0000-4000-8000-000000000081',
  'e2e00001-0000-4000-8000-00000000000a',
  'E2E Priced Kettle', NULL, 59900, 49900, 2, 99800, 20000,
  strftime('%Y-%m-%dT%H:%M:%S.000Z','now'), strftime('%Y-%m-%dT%H:%M:%S.000Z','now')
);

INSERT INTO sale (id, bill_no, payment_method, subtotal_paise, saving_paise, tax_paise, total_paise, operator_name, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-000000000082',
  9002, 'UPI', 49900, 10000, 0, 49900, 'E2E Seed',
  strftime('%Y-%m-%dT%H:%M:%S.000Z','now'), strftime('%Y-%m-%dT%H:%M:%S.000Z','now')
);

INSERT INTO sale_line (id, sale_id, product_id, name, barcode, mrp_paise, unit_price_paise, quantity, line_total_paise, saving_paise, created_at, updated_at)
VALUES (
  'e2e00001-0000-4000-8000-000000000092',
  'e2e00001-0000-4000-8000-000000000082',
  'e2e00001-0000-4000-8000-00000000000a',
  'E2E Priced Kettle', NULL, 59900, 49900, 1, 49900, 10000,
  strftime('%Y-%m-%dT%H:%M:%S.000Z','now'), strftime('%Y-%m-%dT%H:%M:%S.000Z','now')
);
