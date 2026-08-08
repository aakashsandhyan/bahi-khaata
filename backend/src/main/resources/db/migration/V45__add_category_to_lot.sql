-- V45 — a lot may carry its own default category.
--
-- Category has only ever lived on the product, so a lot's category was derived by looking at
-- what is in it (LotCategoryResolver) — which leaves a freshly created manual lot with no
-- category at all until its first product is added. This adds an explicit, optional category to
-- the lot itself: the default for products added to it, still overridable per product so mixed
-- lots remain possible.
--
-- Nullable, and the same SQLite "ADD COLUMN with inline FK, no table rebuild" precedent V38 used
-- for lot.supplier_id. Existing rows stay NULL; the lot list falls back to the derived resolver
-- value for those, so nothing here needs a backfill.

ALTER TABLE lot ADD COLUMN category TEXT REFERENCES category (code);
