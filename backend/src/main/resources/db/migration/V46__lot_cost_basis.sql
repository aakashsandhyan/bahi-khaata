-- V46 — a lot may declare a cost basis: how its products' cost is derived, not apportioned.
--
-- Cost today comes from one of two places: the manifest rate (amount paid over stated value,
-- pinned at counting) or the amount-paid apportionment (CostAllocator, the legacy direct-receive
-- path). Neither can express what the shop actually agrees with a supplier sometimes — a flat
-- per-piece rate, a percentage of MRP or of the online selling price, an MRP-banded rate card, or
-- a multiplier on some base cost. This adds that as an explicit, optional configuration on the
-- lot: a strategy, an anchor (MRP or ASP) the anchor-dependent strategies read, and each
-- strategy's own parameters. A lot that sets none of this keeps behaving exactly as it does today
-- — both existing paths are untouched; a declared basis is a third, opt-in one.
--
-- All money stays integer paise; percent and multiplier are scaled integers (basis points and
-- milli-units) rather than anything fractional, so every numeric column here is BIGINT, never
-- REAL — matching every other money/scaled-integer column in this schema (allocated_unit_cost_
-- paise, mrp_paise, ...) so Hibernate's ddl-auto=validate agrees with what a `long`/`Long` field
-- maps to. The ratio math itself happens transiently in BigDecimal, never stored.
--
-- Same "ADD COLUMN, no table rebuild" precedent as V38 (lot.supplier_id) and V45 (lot.category):
-- every column here is nullable, so every existing lot leaves them all null and is read back
-- exactly as a lot with no declared cost basis. SQLite (3.25+) accepts a CHECK constraint on an
-- ADD COLUMN so long as it does not reference another column of the table, which none of these do.

ALTER TABLE lot ADD COLUMN cost_basis_strategy TEXT
    CHECK (cost_basis_strategy IS NULL
           OR cost_basis_strategy IN ('FLAT_PER_UNIT', 'PERCENT_OF_ANCHOR', 'MRP_RATE_RANGE', 'MULTIPLIER'));

ALTER TABLE lot ADD COLUMN cost_anchor TEXT
    CHECK (cost_anchor IS NULL OR cost_anchor IN ('MRP', 'ASP'));

-- FLAT_PER_UNIT's stated cost, and MULTIPLIER's base when it is an entered cost rather than the
-- anchor or the manifest's stated value.
ALTER TABLE lot ADD COLUMN flat_unit_cost_paise BIGINT
    CHECK (flat_unit_cost_paise IS NULL OR flat_unit_cost_paise > 0);

-- PERCENT_OF_ANCHOR's percentage in basis points: 30% is stored as 3000.
ALTER TABLE lot ADD COLUMN percent_bp BIGINT
    CHECK (percent_bp IS NULL OR percent_bp > 0);

-- MULTIPLIER's factor in milli-units: 1.25x is stored as 1250.
ALTER TABLE lot ADD COLUMN multiplier_milli BIGINT
    CHECK (multiplier_milli IS NULL OR multiplier_milli > 0);

ALTER TABLE lot ADD COLUMN multiplier_base TEXT
    CHECK (multiplier_base IS NULL OR multiplier_base IN ('ENTERED_UNIT_COST', 'ANCHOR', 'STATED_VALUE'));

-- MRP_RATE_RANGE's rate card: a child table, not JSON on the lot, so bands are queryable,
-- checkable, and edited one row at a time like everything else in this schema. min is inclusive,
-- max is exclusive and nullable — null is the open-topped final band. Non-overlapping and
-- ascending is enforced in application code at save time (LotCostBasis.requireValidBasis), not
-- here: SQLite cannot express "no other row of mine overlaps this range" as a CHECK.
CREATE TABLE lot_mrp_rate_band (
    id            CHAR(36) PRIMARY KEY,
    lot_id        CHAR(36) NOT NULL REFERENCES lot (id),
    min_mrp_paise BIGINT   NOT NULL CHECK (min_mrp_paise >= 0),
    max_mrp_paise BIGINT   CHECK (max_mrp_paise IS NULL OR max_mrp_paise > min_mrp_paise),
    cost_paise    BIGINT   NOT NULL CHECK (cost_paise >= 0),
    created_at    TEXT     NOT NULL
);

CREATE INDEX idx_lot_mrp_rate_band_lot ON lot_mrp_rate_band (lot_id);
