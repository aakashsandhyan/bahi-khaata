-- V51 — custom (manual-entry) sale lines.
--
-- The till can sell a thing with no product record: a name and a price keyed at the counter.
-- Such a line has no product, so product_id becomes nullable on both the cart line and the sale
-- line; the cart line carries the keyed name and a GST sub-category (rates are per sub-category),
-- and both carry an OPTIONAL lot reference — attribution to the delivery the thing came from, for
-- recovery reporting, chosen when the operator knows it. No stock ledger entry is written for a
-- custom line: the stock was never in the system, which is precisely why it is keyed by hand.
--
-- SQLite cannot relax NOT NULL in place, so both tables are rebuilt and renamed. Nothing
-- references either table by foreign key, so the rename is clean.

CREATE TABLE cart_line_v51 (
    id               CHAR(36) PRIMARY KEY,
    cart_id          CHAR(36) NOT NULL REFERENCES cart (id),
    product_id       CHAR(36) REFERENCES product (id),
    custom_name      TEXT,
    sub_category     TEXT,
    lot_id           CHAR(36) REFERENCES lot (id),
    unit_price_paise BIGINT   NOT NULL,
    mrp_paise        BIGINT   NOT NULL,
    quantity         BIGINT   NOT NULL,
    created_at       TEXT     NOT NULL,
    updated_at       TEXT     NOT NULL,
    -- A line is either a product's or a keyed custom one — never neither.
    CHECK (product_id IS NOT NULL OR custom_name IS NOT NULL)
);
INSERT INTO cart_line_v51 (id, cart_id, product_id, custom_name, sub_category, lot_id,
                           unit_price_paise, mrp_paise, quantity, created_at, updated_at)
    SELECT id, cart_id, product_id, NULL, NULL, NULL,
           unit_price_paise, mrp_paise, quantity, created_at, updated_at
    FROM cart_line;
DROP TABLE cart_line;
ALTER TABLE cart_line_v51 RENAME TO cart_line;
CREATE INDEX idx_cart_line_cart ON cart_line (cart_id);

CREATE TABLE sale_line_v51 (
    id               CHAR(36) PRIMARY KEY,
    sale_id          CHAR(36) NOT NULL REFERENCES sale (id),
    product_id       CHAR(36) REFERENCES product (id),
    lot_id           CHAR(36) REFERENCES lot (id),
    name             TEXT     NOT NULL,
    barcode          TEXT,
    mrp_paise        BIGINT   NOT NULL,
    unit_price_paise BIGINT   NOT NULL,
    quantity         BIGINT   NOT NULL CHECK (quantity > 0),
    line_total_paise BIGINT   NOT NULL,
    saving_paise     BIGINT   NOT NULL,
    gst_basis_points INTEGER,
    tax_paise        BIGINT   NOT NULL DEFAULT 0,
    created_at       TEXT     NOT NULL,
    updated_at       TEXT     NOT NULL
);
INSERT INTO sale_line_v51 (id, sale_id, product_id, lot_id, name, barcode, mrp_paise,
                           unit_price_paise, quantity, line_total_paise, saving_paise,
                           gst_basis_points, tax_paise, created_at, updated_at)
    SELECT id, sale_id, product_id, NULL, name, barcode, mrp_paise,
           unit_price_paise, quantity, line_total_paise, saving_paise,
           gst_basis_points, tax_paise, created_at, updated_at
    FROM sale_line;
DROP TABLE sale_line;
ALTER TABLE sale_line_v51 RENAME TO sale_line;
CREATE INDEX idx_sale_line_sale ON sale_line (sale_id);
