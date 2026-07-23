-- V23 — the till's working state: a cart being rung up.
--
-- Mutable, unlike the invoice it will become. Lines are added, quantities change, lines are
-- removed, right up until payment — so no immutability triggers here, the opposite of the
-- ledger and the invoice. A cart is scratch paper; the invoice is the record it produces.
--
-- Price and MRP are snapshotted onto the line when it is added, not read live. A sale in
-- progress must not shift because someone repriced the product on the dashboard mid-transaction:
-- what the customer was quoted is what they pay.

CREATE TABLE cart (
    id         CHAR(36) PRIMARY KEY,
    state      TEXT     NOT NULL DEFAULT 'OPEN'
        CHECK (state IN ('OPEN', 'PAID', 'ABANDONED')),
    created_at TEXT     NOT NULL,
    updated_at TEXT     NOT NULL
);

CREATE TABLE cart_line (
    id               CHAR(36) PRIMARY KEY,
    cart_id          CHAR(36) NOT NULL REFERENCES cart (id),
    product_id       CHAR(36) NOT NULL REFERENCES product (id),

    -- Snapshotted at add time, so a mid-sale reprice cannot move what is owed.
    unit_price_paise BIGINT   NOT NULL CHECK (unit_price_paise > 0),
    mrp_paise        BIGINT   NOT NULL CHECK (mrp_paise > 0),

    quantity         BIGINT   NOT NULL CHECK (quantity > 0),

    created_at       TEXT     NOT NULL,
    updated_at       TEXT     NOT NULL,

    -- One line per product; scanning the same thing twice raises its quantity.
    UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_cart_line_cart ON cart_line (cart_id);
