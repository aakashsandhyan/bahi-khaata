-- V25 — a fourth condition, for goods that must be prepared before they can be sold.
--
-- GOOD sells now, DAMAGED sells cheaper, UNUSABLE never sells. NEEDS_WORK is none of these: it
-- works or is fixable, and will sell at full price once cleaned, repaired, or rebuilt. So it costs
-- what any sellable unit of the delivery costs — it belongs in the divisor at close, like GOOD and
-- DAMAGED — but it is not yet on the shelf, so, like UNUSABLE, it stays off the stock ledger until
-- moved to a sellable state. Owned, costed, not yet sellable.
--
-- ## The issue type splits the batch key
--
-- A needs-work unit carries the kind of work it needs (V24's issue types). One product may hold
-- units needing different work — some to clean, some to repair — so the uniqueness that held one
-- batch per product per condition widens to include the issue type. It is null for the other three
-- conditions and required for NEEDS_WORK; a CHECK ties the two together.
--
-- The old table-level UNIQUE cannot express this, because two null issue types read as distinct in
-- SQLite and would let a product keep two GOOD batches. So it becomes a unique index that coalesces
-- a null issue type to a blank, restoring the old guarantee for the three conditions that have none
-- while letting needs-work split by the work it needs.
--
-- Non-transactional, foreign keys off, for the same reason as V13/V17/V20: stock_ledger references
-- batch, SQLite rewrites those references on RENAME, and that needs foreign keys off, which a
-- transaction will not allow.

PRAGMA foreign_keys = OFF;

CREATE TABLE batch_rebuilt (
    id                        CHAR(36) PRIMARY KEY,
    product_id                CHAR(36) NOT NULL REFERENCES product (id),
    lot_id                    CHAR(36) NOT NULL REFERENCES lot (id),

    condition                 TEXT     NOT NULL DEFAULT 'GOOD'
        CHECK (condition IN ('GOOD', 'DAMAGED', 'UNUSABLE', 'NEEDS_WORK')),

    -- Set exactly when the goods need work, naming that work; null for every other condition.
    issue_type                TEXT     REFERENCES issue_type (code)
        CHECK ((condition = 'NEEDS_WORK') = (issue_type IS NOT NULL)),

    allocated_total_paise     BIGINT   CHECK (allocated_total_paise IS NULL OR allocated_total_paise >= 0),
    allocated_unit_cost_paise BIGINT   CHECK (allocated_unit_cost_paise IS NULL OR allocated_unit_cost_paise >= 0),

    cost_basis                TEXT
        CHECK (cost_basis IS NULL
               OR cost_basis IN ('ALLOCATED', 'PINNED', 'IMPORTED', 'ESTIMATED', 'ABSORBED')),

    quantity_received         BIGINT   NOT NULL CHECK (quantity_received >= 0),
    quantity_damaged          BIGINT   NOT NULL DEFAULT 0 CHECK (quantity_damaged >= 0),

    mrp_paise                 BIGINT   CHECK (mrp_paise IS NULL OR mrp_paise > 0),
    mrp_is_estimate           BOOLEAN  NOT NULL DEFAULT 0,
    labelled_at               TEXT,

    created_at                TEXT     NOT NULL,
    updated_at                TEXT     NOT NULL,
    remark                    TEXT,

    CHECK (quantity_damaged <= quantity_received)
);

INSERT INTO batch_rebuilt (
    id, product_id, lot_id, condition, issue_type, allocated_total_paise, allocated_unit_cost_paise,
    cost_basis, quantity_received, quantity_damaged, mrp_paise, mrp_is_estimate, labelled_at,
    created_at, updated_at, remark)
SELECT
    id, product_id, lot_id, condition, NULL, allocated_total_paise, allocated_unit_cost_paise,
    cost_basis, quantity_received, quantity_damaged, mrp_paise, mrp_is_estimate, labelled_at,
    created_at, updated_at, remark
FROM batch;

DROP TABLE batch;
ALTER TABLE batch_rebuilt RENAME TO batch;

CREATE INDEX idx_batch_product_lot ON batch (product_id, lot_id);
CREATE UNIQUE INDEX idx_batch_identity
    ON batch (lot_id, product_id, condition, COALESCE(issue_type, ''));

PRAGMA foreign_keys = ON;
