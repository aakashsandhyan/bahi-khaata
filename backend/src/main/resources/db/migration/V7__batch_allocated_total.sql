-- V7 — the allocated total per batch line.
--
-- A unit cost alone cannot satisfy "allocated costs sum to the lot amount exactly". A share
-- of ₹1.00 over three sellable units is ₹0.33 each, and 0.33 × 3 is ₹0.99 — a paise
-- disappears on every line of every lot, and the books drift for no discoverable reason.
--
-- So the line's whole share is stored, and reconciliation is exact against it:
--
--     SUM(allocated_total_paise) over a lot = amount_paid + freight
--
-- allocated_unit_cost_paise stays, derived as share ÷ sellable quantity, and remains what
-- cost of goods sold and margin display use. It is the rounded, per-unit view; the total is
-- the authoritative figure.
--
-- Backfilled as unit cost × sellable quantity. That is exactly right for every row written
-- before this migration, because those were built from a unit cost in the first place — no
-- allocation had yet produced a share that could disagree.

ALTER TABLE batch ADD COLUMN allocated_total_paise BIGINT NOT NULL DEFAULT 0;

UPDATE batch
SET allocated_total_paise =
        allocated_unit_cost_paise * (quantity_received - quantity_damaged);
