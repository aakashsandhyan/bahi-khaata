-- V16 — an ASIN is not a barcode, and must stop claiming to be one.
--
-- The importer recorded each manifest ASIN as the product's barcode with origin MANUFACTURER.
-- That is wrong twice. An ASIN is Amazon's catalogue identifier, not a code any manufacturer
-- assigned, and it is not printed on the goods at all — what is actually on the pack is the
-- maker's EAN or UPC, or an Amazon FNSKU sticker on returns.
--
-- Found by scanning a real carton: the item in the hand and the line on the sheet are the same
-- product, and the codes do not match. They never would.
--
-- So MARKETPLACE joins the set. The ASIN stays recorded — it is how a line is matched back to
-- the manifest, and it is what a future price lookup would use — but it stops passing itself
-- off as something a scanner will ever read off a box.
--
-- The code that IS on the pack gets added as a second barcode for the same product, the first
-- time someone scans it and says which line it belongs to. A product may hold several codes:
-- `barcode.code` is unique, `barcode.product_id` deliberately is not.
--
-- The table is rebuilt because SQLite cannot alter a CHECK constraint. It has no children, so
-- unlike the batch rebuild in V13 this needs no foreign-key gymnastics and stays transactional.

CREATE TABLE barcode_rebuilt (
    product_id  CHAR(36) NOT NULL REFERENCES product (id),
    code        TEXT     NOT NULL UNIQUE,
    origin      TEXT     NOT NULL
        CHECK (origin IN ('MANUFACTURER', 'INTERNAL', 'MARKETPLACE')),
    id          CHAR(36) PRIMARY KEY,
    created_at  TEXT     NOT NULL
);

INSERT INTO barcode_rebuilt (product_id, code, origin, id, created_at)
SELECT product_id, code, origin, id, created_at FROM barcode;

DROP TABLE barcode;
ALTER TABLE barcode_rebuilt RENAME TO barcode;

-- Every code imported from a manifest is a marketplace identifier, not a manufacturer's.
-- Correcting the existing rows rather than leaving them mislabelled: they were all written by
-- the importer, and none of them came off a physical pack.
UPDATE barcode SET origin = 'MARKETPLACE' WHERE origin = 'MANUFACTURER';
