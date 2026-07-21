-- V21 — a batch may be corrected back to nothing.
--
-- Somebody tags a code to the wrong product, or counts an item against the wrong line, and
-- notices a second later. Until now there was no way back: the count stood, the mapping stood,
-- and the only remedy was to remember the mistake and work around it forever.
--
-- Correcting it means taking the units off again, and where only one was counted that leaves a
-- batch of none. `quantity_received > 0` forbade exactly that, so the last unit could never be
-- undone.
--
-- The batch cannot simply be deleted instead. `stock_ledger` refers to it, and the ledger is
-- append-only by trigger — the receipt that was written stays written, because that is what an
-- append-only ledger means. The correction is a new entry against the same batch, and the batch
-- has to remain for it to point at.
--
-- So zero becomes a legitimate quantity: goods that were counted and then taken back off. Such
-- a batch takes no share when the delivery is costed, because there is nothing there to carry
-- it — see LotClosing, which excludes it rather than asking the allocator to divide by nothing.

PRAGMA foreign_keys = OFF;

CREATE TABLE batch_rebuilt (
    id                        CHAR(36) PRIMARY KEY,
    product_id                CHAR(36) NOT NULL REFERENCES product (id),
    lot_id                    CHAR(36) NOT NULL REFERENCES lot (id),

    condition                 TEXT     NOT NULL DEFAULT 'GOOD'
        CHECK (condition IN ('GOOD', 'DAMAGED', 'UNUSABLE')),

    allocated_total_paise     BIGINT   CHECK (allocated_total_paise IS NULL OR allocated_total_paise >= 0),
    allocated_unit_cost_paise BIGINT   CHECK (allocated_unit_cost_paise IS NULL OR allocated_unit_cost_paise >= 0),

    cost_basis                TEXT
        CHECK (cost_basis IS NULL
               OR cost_basis IN ('ALLOCATED', 'PINNED', 'IMPORTED', 'ESTIMATED', 'ABSORBED')),

    -- Zero is a real state: counted, then corrected back off. Never negative, which would be
    -- a correction larger than the thing it corrects.
    quantity_received         BIGINT   NOT NULL CHECK (quantity_received >= 0),
    quantity_damaged          BIGINT   NOT NULL DEFAULT 0 CHECK (quantity_damaged >= 0),

    mrp_paise                 BIGINT   CHECK (mrp_paise IS NULL OR mrp_paise > 0),
    mrp_is_estimate           BOOLEAN  NOT NULL DEFAULT 0,
    labelled_at               TEXT,

    created_at                TEXT     NOT NULL,
    updated_at                TEXT     NOT NULL,

    CHECK (quantity_damaged <= quantity_received),
    UNIQUE (lot_id, product_id, condition)
);

INSERT INTO batch_rebuilt (
    id, product_id, lot_id, condition, allocated_total_paise, allocated_unit_cost_paise,
    cost_basis, quantity_received, quantity_damaged, mrp_paise, mrp_is_estimate, labelled_at,
    created_at, updated_at)
SELECT
    id, product_id, lot_id, condition, allocated_total_paise, allocated_unit_cost_paise,
    cost_basis, quantity_received, quantity_damaged, mrp_paise, mrp_is_estimate, labelled_at,
    created_at, updated_at
FROM batch;

DROP TABLE batch;
ALTER TABLE batch_rebuilt RENAME TO batch;

CREATE INDEX idx_batch_product_lot ON batch (product_id, lot_id);

PRAGMA foreign_keys = ON;
