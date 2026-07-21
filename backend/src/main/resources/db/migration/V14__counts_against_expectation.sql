-- V14 — what has actually been counted, recorded against the line it was expected on.
--
-- A gap in V12. Stock lands in `batch`, which is per product per lot, so it sums across every
-- carton a product arrives in — 449 products in the first consignment came in more than one,
-- one of them across 24. That makes a batch unable to answer the question the unpacking screen
-- asks constantly: what is left to find in *this* box.
--
-- Without it, someone who counts half a carton and goes home cannot be shown where they were.
-- A part-counted box is a normal state to walk away from at closing time, so resuming has to
-- work, and resuming needs counts held per line.

-- Counted, not expected. The two are kept side by side because their difference is the point:
-- it is the only record that twelve were promised where eleven arrived.
ALTER TABLE expected_line ADD COLUMN quantity_counted BIGINT NOT NULL DEFAULT 0;

-- Goods found in a carton that no line names. They still take a share of the lot, because the
-- money bought whatever arrived, and they are weighed at the lot's average unit value when it
-- closes.
--
-- Kept apart from `expected_line` rather than inserted there with a zero expectation: an
-- expected line records a claim the supplier made, and writing our own discovery into that
-- table would blur the one distinction this whole change exists to preserve. It also avoids
-- rebuilding the table to relax its positive-quantity constraint.
CREATE TABLE unlisted_find (
    id         CHAR(36) PRIMARY KEY,
    lot_id     CHAR(36) NOT NULL REFERENCES lot (id),
    box_id     CHAR(36) NOT NULL REFERENCES box (id),
    product_id CHAR(36) NOT NULL REFERENCES product (id),

    quantity   BIGINT   NOT NULL CHECK (quantity > 0),

    created_at TEXT     NOT NULL,
    updated_at TEXT     NOT NULL,

    UNIQUE (box_id, product_id)
);

CREATE INDEX idx_unlisted_find_lot ON unlisted_find (lot_id);
