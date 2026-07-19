-- V4 — the counter for internally generated barcodes.
--
-- A single monotonic integer, unlike the UUIDs everywhere else: internal codes are
-- BBZ-100000, BBZ-100001, … and the sequence must be dense and gapless-enough that they
-- stay a fixed six digits. Starting at 100000 means every value is six digits until the
-- range is exhausted at 999999, so no zero-padding logic is needed.
--
-- One row, enforced by the PK plus CHECK (id = 1). last_seq holds the most recently
-- allocated value; allocation increments and returns it in a single atomic statement, so
-- two goods-in entries cannot claim the same number. Seeded at 99999 so the first
-- allocation yields 100000.

CREATE TABLE internal_barcode_counter (
    id       INTEGER PRIMARY KEY CHECK (id = 1),
    last_seq BIGINT  NOT NULL
);

INSERT INTO internal_barcode_counter (id, last_seq) VALUES (1, 99999);
