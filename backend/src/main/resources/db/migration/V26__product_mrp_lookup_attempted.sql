-- V26 — remember which products the MRP lookup has already tried.
--
-- A found price is stored on the batch, so a priced product drops out of the work list on its own.
-- A product whose listing has no printed price — or none the source will serve — stays unpriced,
-- and without a mark it would be scraped again on every background fill, spending the rate budget
-- on the same dead ends forever.
--
-- So the attempt itself is recorded, found or not. The background fill looks up each product once
-- and never again; a deliberate per-item suggestion in the pane is unaffected and may still be
-- asked, since a person is holding the goods and asking on purpose.

ALTER TABLE product ADD COLUMN mrp_lookup_attempted_at TEXT;
