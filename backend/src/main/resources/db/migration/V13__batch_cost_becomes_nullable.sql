-- V13 — a batch may hold stock before anyone knows what it cost.
--
-- Counting now creates the batch, and a lot's shares cannot be settled until every box is
-- open, so there is a real interval where stock is held and its cost is genuinely unknown.
-- The three cost columns therefore accept null, and null means "not yet apportioned".
--
-- That is NOT zero, and the distinction is the whole point of this migration. A batch costing
-- nothing makes every margin computed from it meaningless and shows up on a report as pure
-- profit. Nullable columns make the database itself refuse to answer, instead of answering
-- wrongly — which matters here because two cost bugs this week were invisible precisely
-- because something plausible was returned rather than nothing.
--
-- ESTIMATED joins cost_basis: goods that arrive without appearing on the manifest have no
-- stated value and are weighed at the lot's average unit value. Right on average, wrong on
-- any particular item, so it is recorded as an estimate rather than passed off as allocated.
--
-- ## Why this one runs outside a transaction
--
-- SQLite cannot drop a NOT NULL constraint, so the table must be rebuilt. `stock_ledger`
-- references `batch`, and modern SQLite rewrites those references when a table is renamed —
-- so the rebuild needs `PRAGMA foreign_keys = OFF`, which is a no-op inside a transaction.
-- `defer_foreign_keys` does not help: the failure is at RENAME, not at DROP.
--
-- The cost is that a failure here leaves the migration half-applied, with no rollback. It is
-- kept deliberately small for that reason — V12 does all the additive work transactionally,
-- and this file does nothing but the rebuild. Verified against a copy of the live database
-- before being committed: 1,878 batches preserved, every ledger reference intact, no foreign
-- key violations afterwards.
--
-- If it does fail, the recovery is `flyway repair` and a restore, not a partial re-run.

PRAGMA foreign_keys = OFF;

CREATE TABLE batch_rebuilt (
    id                        CHAR(36) PRIMARY KEY,
    product_id                CHAR(36) NOT NULL REFERENCES product (id),
    lot_id                    CHAR(36) NOT NULL REFERENCES lot (id),

    allocated_total_paise     BIGINT   CHECK (allocated_total_paise IS NULL OR allocated_total_paise >= 0),
    allocated_unit_cost_paise BIGINT   CHECK (allocated_unit_cost_paise IS NULL OR allocated_unit_cost_paise >= 0),

    cost_basis                TEXT
        CHECK (cost_basis IS NULL
               OR cost_basis IN ('ALLOCATED', 'PINNED', 'IMPORTED', 'ESTIMATED')),

    quantity_received         BIGINT   NOT NULL CHECK (quantity_received > 0),
    quantity_damaged          BIGINT   NOT NULL DEFAULT 0 CHECK (quantity_damaged >= 0),

    mrp_paise                 BIGINT   CHECK (mrp_paise IS NULL OR mrp_paise > 0),
    mrp_is_estimate           BOOLEAN  NOT NULL DEFAULT 0,

    created_at                TEXT     NOT NULL,
    updated_at                TEXT     NOT NULL,

    CHECK (quantity_damaged <= quantity_received),
    UNIQUE (lot_id, product_id)
);

INSERT INTO batch_rebuilt (
    id, product_id, lot_id, allocated_total_paise, allocated_unit_cost_paise, cost_basis,
    quantity_received, quantity_damaged, mrp_paise, mrp_is_estimate, created_at, updated_at)
SELECT
    id, product_id, lot_id, allocated_total_paise, allocated_unit_cost_paise, cost_basis,
    quantity_received, quantity_damaged, mrp_paise, mrp_is_estimate, created_at, updated_at
FROM batch;

DROP TABLE batch;
ALTER TABLE batch_rebuilt RENAME TO batch;

CREATE INDEX idx_batch_product_lot ON batch (product_id, lot_id);

-- Restored explicitly: this connection returns to the pool, and the application relies on
-- foreign keys being enforced on every connection it takes out.
PRAGMA foreign_keys = ON;
