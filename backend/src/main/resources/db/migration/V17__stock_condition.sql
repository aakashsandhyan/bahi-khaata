-- V17 — damaged goods are stock worth less, not stock lost.
--
-- The model until now said a damaged unit is a write-off: excluded from the cost divisor so its
-- share is absorbed by the good units, which are then priced higher to cover it. That is right
-- for something fit for the bin. It is wrong for this shop, where a scratched item sells cheaper
-- and selling it is the business.
--
-- Under the old model that misreported twice. The damaged unit carried no cost, so selling it
-- for ₹50 read as ₹50 of pure profit; and the good units carried cost that was never theirs, so
-- their margin read thinner than it was.
--
-- So a condition is recorded, and both conditions are ordinary stock at the ordinary cost. What
-- differs is what they sell for, which is a judgement made later by someone who can see cost —
-- not by the person holding the carton.
--
-- ## Why a separate row rather than a count
--
-- `stock_ledger` keys on batch. Selling a damaged unit has to take stock from the damaged pile
-- and leave the good one alone, and a valuation has to be able to tell them apart. A count on a
-- single batch cannot do either: the ledger would have no way to say which kind left.
--
-- So one product in one delivery may now have two batches, and the uniqueness that enforced
-- one is widened to include the condition. SQLite cannot alter a table constraint, so `batch`
-- is rebuilt — the same procedure as V13, and non-transactional for the same reason:
-- `stock_ledger` references it, SQLite rewrites those references on RENAME, and the rebuild
-- therefore needs foreign keys off, which a transaction will not allow.
--
-- `quantity_damaged` stays. It still means what it always did — units that arrived unsellable
-- and were written off — which is a different thing from a unit sold as seconds. Both remain
-- expressible, because both really happen: some goods are worth less, and some are worth
-- nothing.

PRAGMA foreign_keys = OFF;

CREATE TABLE batch_rebuilt (
    id                        CHAR(36) PRIMARY KEY,
    product_id                CHAR(36) NOT NULL REFERENCES product (id),
    lot_id                    CHAR(36) NOT NULL REFERENCES lot (id),

    -- GOOD is the ordinary case and the default, so every existing row is correct without
    -- anyone deciding anything.
    condition                 TEXT     NOT NULL DEFAULT 'GOOD'
        CHECK (condition IN ('GOOD', 'DAMAGED')),

    allocated_total_paise     BIGINT   CHECK (allocated_total_paise IS NULL OR allocated_total_paise >= 0),
    allocated_unit_cost_paise BIGINT   CHECK (allocated_unit_cost_paise IS NULL OR allocated_unit_cost_paise >= 0),

    cost_basis                TEXT
        CHECK (cost_basis IS NULL
               OR cost_basis IN ('ALLOCATED', 'PINNED', 'IMPORTED', 'ESTIMATED')),

    quantity_received         BIGINT   NOT NULL CHECK (quantity_received > 0),
    quantity_damaged          BIGINT   NOT NULL DEFAULT 0 CHECK (quantity_damaged >= 0),

    mrp_paise                 BIGINT   CHECK (mrp_paise IS NULL OR mrp_paise > 0),
    mrp_is_estimate           BOOLEAN  NOT NULL DEFAULT 0,
    labelled_at               TEXT,

    created_at                TEXT     NOT NULL,
    updated_at                TEXT     NOT NULL,

    CHECK (quantity_damaged <= quantity_received),

    -- Widened from (lot_id, product_id). One product in one delivery may arrive in two
    -- conditions and they must be kept apart, but there is still no reason for two rows of the
    -- same product in the same condition.
    UNIQUE (lot_id, product_id, condition)
);

INSERT INTO batch_rebuilt (
    id, product_id, lot_id, condition, allocated_total_paise, allocated_unit_cost_paise,
    cost_basis, quantity_received, quantity_damaged, mrp_paise, mrp_is_estimate, labelled_at,
    created_at, updated_at)
SELECT
    id, product_id, lot_id, 'GOOD', allocated_total_paise, allocated_unit_cost_paise,
    cost_basis, quantity_received, quantity_damaged, mrp_paise, mrp_is_estimate, labelled_at,
    created_at, updated_at
FROM batch;

DROP TABLE batch;
ALTER TABLE batch_rebuilt RENAME TO batch;

CREATE INDEX idx_batch_product_lot ON batch (product_id, lot_id);

PRAGMA foreign_keys = ON;
