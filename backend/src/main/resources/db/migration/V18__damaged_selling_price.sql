-- V18 — what damaged goods sell for.
--
-- A scratched item sells cheaper than a clean one, so the same product needs two prices. This
-- does NOT move price onto the batch: "selling price belongs to the product, never to a batch"
-- is a settled decision that FIFO costing depends on, and it still holds. The product simply
-- carries two prices now, one per condition, rather than one.
--
-- Nullable and separate from the ordinary price, because the two are decided at different
-- moments by different people. The person unpacking marks an item damaged and moves on; what it
-- is then worth is a judgement for someone who can see what it cost, and it may sit undecided
-- for days. A damaged unit with no damaged price is simply not yet sellable, which is the same
-- rule the ordinary price already follows.

ALTER TABLE product ADD COLUMN damaged_selling_price_paise BIGINT
    CHECK (damaged_selling_price_paise IS NULL OR damaged_selling_price_paise > 0);
