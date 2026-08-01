-- V40 — add the TOYS category.
--
-- Categories are data (see V10), so adding one is an insert. No amazon_product_line yet — it can be
-- set later if a supplier's manifest needs mapping by code. No margin row of its own, so a TOYS
-- product's suggested price falls through to the global default margin until someone sets one.

INSERT INTO category (code, name, amazon_product_line, created_at, updated_at) VALUES
    ('TOYS', 'Toys', NULL, '2026-08-01T00:00:00.000Z', '2026-08-01T00:00:00.000Z');
