-- V20 — goods that arrived fit for nothing.
--
-- Damaged stock splits in two. Something scratched still sells, cheaper, and that is the
-- business. Something broken sells at no price at all, and pretending otherwise would leave it
-- sitting on a shelf forever waiting for a figure nobody can name.
--
-- ## What it costs
--
-- Nothing. Its share is absorbed by the goods that can actually be sold, which then carry the
-- whole amount and are priced accordingly. That is what the pallet really cost to get sellable
-- stock out of, and it is the same rule this schema has always applied to units damaged on
-- arrival.
--
-- The alternative — letting scrap carry cost so the loss shows as a loss — was considered and
-- rejected for now. It answers a different question, about the supplier rather than the goods.
-- That question stays answerable: the condition is recorded per batch, so "how much of this
-- delivery was scrap, and what was it worth on the sheet" can be asked at any time without
-- changing how anything was costed.
--
-- ## Why it never enters the ledger
--
-- The ledger holds stock that exists to be sold. Unusable goods never became that, so writing a
-- receipt and immediately reversing it would add two entries that cancel out and one more way
-- for on-hand to be briefly wrong. The batch records that they arrived; that is the trail.
--
-- ABSORBED joins cost_basis to say plainly where the money went, rather than leaving a zero
-- that reads like a mistake.

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

    quantity_received         BIGINT   NOT NULL CHECK (quantity_received > 0),
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
