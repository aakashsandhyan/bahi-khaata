-- V19 — a returns sticker is a code for one item, not for a product.
--
-- Amazon returns arrive with an LPN label stuck over the manufacturer's barcode, so the
-- printed EAN cannot be scanned and the LPN is the only thing a scanner can read. An LPN
-- identifies one physical unit: the next of the same product carries a different one.
--
-- That is workable — many codes may point at one product, which is why `barcode.product_id`
-- was never unique — but it is a different kind of code and recording it as MANUFACTURER says
-- something untrue. No manufacturer assigned it, and unlike an EAN it will never be seen again.
--
-- The distinction earns its place downstream. A code that identifies a unit cannot be reused on
-- our own label, so goods reaching the shelf need an internal BBZ- code instead; and "how many
-- of these products have a reusable barcode" is a question worth being able to ask.

CREATE TABLE barcode_rebuilt (
    product_id  CHAR(36) NOT NULL REFERENCES product (id),
    code        TEXT     NOT NULL UNIQUE,
    origin      TEXT     NOT NULL
        CHECK (origin IN ('MANUFACTURER', 'INTERNAL', 'MARKETPLACE', 'UNIT_LABEL')),
    id          CHAR(36) PRIMARY KEY,
    created_at  TEXT     NOT NULL
);

INSERT INTO barcode_rebuilt (product_id, code, origin, id, created_at)
SELECT product_id, code, origin, id, created_at FROM barcode;

DROP TABLE barcode;
ALTER TABLE barcode_rebuilt RENAME TO barcode;

-- Correcting what has already been scanned. Every LPN recorded so far went in as MANUFACTURER
-- because nothing knew the difference yet.
UPDATE barcode SET origin = 'UNIT_LABEL' WHERE origin = 'MANUFACTURER' AND code LIKE 'LPN%';
