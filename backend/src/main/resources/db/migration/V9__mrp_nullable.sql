-- V9 — MRP becomes optional, and stops being the allocation weight.
--
-- Two things were conflated and are now separated.
--
-- MRP is the maximum retail price the manufacturer prints on the pack. Under the Legal
-- Metrology (Packaged Commodities) Rules it must be printed, and selling above it is
-- unlawful — so it is a legal figure, read off the goods, not something to be derived.
-- Some stock arrives without one, and pretending otherwise would mean inventing a number
-- that a customer may later read on a shelf card.
--
-- The weight used to apportion a lot's cost is a different thing entirely. A manifest may
-- state an Amazon selling price, a supplier's own cost, or nothing at all. That figure is an
-- input to the arithmetic and is not what goes on a label.
--
-- So MRP is nullable, and the allocator now takes its weight as an argument. A product with
-- no MRP is received and counted, but cannot be sold: without it there is no label, no
-- lawful ceiling on the price, and no saving to show against.
--
-- SQLite cannot alter a column's constraints, so the table is rebuilt. No PRAGMA is used to
-- suspend foreign keys during the swap: Flyway runs each migration in a transaction, inside
-- which that pragma is a no-op, and mixing it with DDL is refused outright. The rebuild is
-- safe without it because this runs before any batch exists — nothing in stock_ledger can
-- reference a row being moved. Were there live data, this would need the migration taken out
-- of its transaction instead.

CREATE TABLE batch_rebuilt (
    id                        CHAR(36) PRIMARY KEY,
    product_id                CHAR(36) NOT NULL REFERENCES product (id),
    lot_id                    CHAR(36) NOT NULL REFERENCES lot (id),

    allocated_total_paise     BIGINT   NOT NULL DEFAULT 0,
    allocated_unit_cost_paise BIGINT   NOT NULL CHECK (allocated_unit_cost_paise >= 0),

    cost_basis                TEXT     NOT NULL
        CHECK (cost_basis IN ('ALLOCATED', 'PINNED', 'IMPORTED')),

    quantity_received         BIGINT   NOT NULL CHECK (quantity_received > 0),
    quantity_damaged          BIGINT   NOT NULL DEFAULT 0 CHECK (quantity_damaged >= 0),

    -- Nullable now. Null means nobody has read it off the goods yet, which is a normal
    -- state for stock that has arrived but not been unpacked and tagged.
    mrp_paise                 BIGINT   CHECK (mrp_paise IS NULL OR mrp_paise > 0),
    mrp_is_estimate           BOOLEAN  NOT NULL DEFAULT 0,

    created_at                TEXT     NOT NULL,
    updated_at                TEXT     NOT NULL,

    CHECK (quantity_damaged <= quantity_received),
    UNIQUE (lot_id, product_id)
);

INSERT INTO batch_rebuilt
SELECT id, product_id, lot_id, allocated_total_paise, allocated_unit_cost_paise,
       cost_basis, quantity_received, quantity_damaged, mrp_paise, mrp_is_estimate,
       created_at, updated_at
FROM batch;

DROP TABLE batch;
ALTER TABLE batch_rebuilt RENAME TO batch;

CREATE INDEX idx_batch_product_lot ON batch (product_id, lot_id);
