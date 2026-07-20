-- V5 — purchases (lots) and the per-product arrivals they contain (batches).
--
-- A lot is what was actually paid: one pallet, one sum. Per-product costs are derived
-- from it by allocation, never entered directly, so the lot amount remains the auditable
-- figure and the batch costs always reconcile back to it.
--
-- Declared types follow design decision 3: CHAR(36) ids, BIGINT for a Java `long`,
-- BOOLEAN for a boolean, TEXT for strings and for ISO-8601 timestamps.
--
-- No immutability triggers here, unlike stock_ledger and invoice. Lots and batches are
-- frozen *on consumption*, not from creation — editable while no stock from the lot has
-- been sold, refused thereafter (design decision 16). That condition depends on the
-- ledger rather than on the row itself, so it is enforced in application logic.

CREATE TABLE lot (
    id                CHAR(36) PRIMARY KEY,

    supplier          TEXT     NOT NULL,

    -- The business delivery date, not a system stamp. FIFO consumes by this, so a
    -- delivery logged two days late still consumes in true arrival order. ISO-8601
    -- date text (2026-07-20) — readable in a sqlite3 session and sorts chronologically.
    received_on       TEXT     NOT NULL,

    -- What was actually paid for the whole lot, and the freight to get it here. Both
    -- are allocated across the batches; freight is part of landed cost.
    amount_paid_paise BIGINT   NOT NULL CHECK (amount_paid_paise >= 0),
    freight_paise     BIGINT   NOT NULL DEFAULT 0 CHECK (freight_paise >= 0),

    -- Provenance: which method produced this lot's figures. Recorded so a cost can be
    -- judged later, and so changing the method never silently re-costs old lots.
    allocation_method TEXT     NOT NULL
        CHECK (allocation_method IN ('RELATIVE_MRP', 'FULLY_PINNED', 'IMPORTED')),

    created_at        TEXT     NOT NULL,
    updated_at        TEXT     NOT NULL
);

CREATE TABLE batch (
    id                        CHAR(36) PRIMARY KEY,
    product_id                CHAR(36) NOT NULL REFERENCES product (id),
    lot_id                    CHAR(36) NOT NULL REFERENCES lot (id),

    -- Derived from the lot amount by allocation. Never entered directly except when
    -- pinned, which cost_basis records.
    allocated_unit_cost_paise BIGINT   NOT NULL CHECK (allocated_unit_cost_paise >= 0),

    -- How this cost was arrived at. Unreconstructable after the fact, so captured now.
    cost_basis                TEXT     NOT NULL
        CHECK (cost_basis IN ('ALLOCATED', 'PINNED', 'IMPORTED')),

    -- Damaged units are received but not sellable: excluded from the divisor, so their
    -- share of the cost is absorbed by the units that can actually be sold.
    quantity_received         BIGINT   NOT NULL CHECK (quantity_received > 0),
    quantity_damaged          BIGINT   NOT NULL DEFAULT 0 CHECK (quantity_damaged >= 0),

    -- MRP drives allocation, so it is required — printed where known, estimated
    -- otherwise, with the flag recording which. An estimate must never be mistaken for
    -- a printed price.
    mrp_paise                 BIGINT   NOT NULL CHECK (mrp_paise > 0),
    mrp_is_estimate           BOOLEAN  NOT NULL DEFAULT 0,

    created_at                TEXT     NOT NULL,
    updated_at                TEXT     NOT NULL,

    -- Table constraints, which SQLite requires after every column definition.
    --
    -- More units damaged than arrived is impossible, and would make the sellable
    -- quantity negative — which would invert the cost allocation.
    CHECK (quantity_damaged <= quantity_received),

    -- One line per product per lot: two lines for the same product in one delivery are
    -- a data-entry mistake, and would split its allocation in half.
    UNIQUE (lot_id, product_id)
);

-- FIFO consumes the oldest arrival first, and reads batches by product. Ordering is by
-- the lot's business date, so the index carries product and lot together.
CREATE INDEX idx_batch_product_lot ON batch (product_id, lot_id);
