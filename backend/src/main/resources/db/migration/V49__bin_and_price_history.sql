-- V45 — physical bin locations on batches, and an append-only price-change journal.
--
-- Two independent additions for the Inventory screen (see openspec/changes/palletworks-inventory):
--
-- 1. `batch.bin` — a nullable, free-text tag naming where a batch's stock physically sits.
--    A plain `ADD COLUMN`, not a rebuild: nullable with no default is a metadata-only change in
--    SQLite, unlike V25's rebuild of this same table, which was driven by a constraint change
--    (the identity index) that `ADD COLUMN` cannot express. No registry or lookup table — a bin
--    is an operator's own shorthand, stored verbatim (design decision D7).
--
-- 2. `price_history` — one row per selling-price change, written from the single pricing choke
--    point (`ProductPricing.setSellingPrice`), never by a caller directly. `old_price_paise` is
--    NULL on a product's first-ever price set. Append-only, guarded by the same no-UPDATE/
--    no-DELETE trigger pair as `stock_ledger` (V6) — being immutable, it carries only a creation
--    time and no `updated_at`. `operator_name` is nullable: the choke point's signature carries
--    no operator today (D6), and a later change can backfill the column without a rewrite.

ALTER TABLE batch ADD COLUMN bin TEXT;

CREATE TABLE price_history (
    id               CHAR(36) PRIMARY KEY,
    product_id       CHAR(36) NOT NULL REFERENCES product (id),

    -- NULL on the product's first-ever price set; otherwise the price it carried immediately
    -- before this change.
    old_price_paise  BIGINT,

    new_price_paise  BIGINT   NOT NULL CHECK (new_price_paise > 0),

    -- Who made the change, or NULL — not captured yet at the choke point (D6), not "no operator".
    operator_name    TEXT,

    created_at       TEXT     NOT NULL
);

-- Price history is read newest-first for one product at a time (item detail).
CREATE INDEX idx_price_history_product_created ON price_history (product_id, created_at);

-- Append-only, enforced by the database itself — mirrors stock_ledger_no_update/no_delete (V6).
CREATE TRIGGER price_history_no_update
BEFORE UPDATE ON price_history
BEGIN
    SELECT RAISE(ABORT, 'price_history is append-only: rows cannot be updated');
END;

CREATE TRIGGER price_history_no_delete
BEFORE DELETE ON price_history
BEGIN
    SELECT RAISE(ABORT, 'price_history is append-only: rows cannot be deleted');
END;
