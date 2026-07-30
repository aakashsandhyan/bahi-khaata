-- V35 — Record when a product's label was last printed.
--
-- Unset until a label for the product prints successfully, set at that moment. Lets the bulk
-- print screen list shelf products still awaiting a label, and reprints leave it set. Stored as
-- ISO-8601 text, the convention for Instant columns in this schema.

ALTER TABLE product ADD COLUMN label_printed_at TEXT;
