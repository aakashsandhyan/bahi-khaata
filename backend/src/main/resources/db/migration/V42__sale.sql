-- V42 — a completed sale and its lines.
--
-- Completing a cart records a `sale` with a running bill number and snapshotted lines. The sale is
-- immutable: a later price or name change must never alter a past bill. `tax_paise` is 0 for now —
-- the shop bills as a composition Bill of Supply (no tax collected) — but the column is here so a
-- tax figure can be recorded later without a schema change.

CREATE TABLE sale (
    id               CHAR(36) PRIMARY KEY,
    bill_no          BIGINT   NOT NULL,
    payment_method   TEXT     NOT NULL CHECK (payment_method IN ('CASH', 'UPI', 'CARD')),
    subtotal_paise   BIGINT   NOT NULL,
    saving_paise     BIGINT   NOT NULL,
    tax_paise        BIGINT   NOT NULL DEFAULT 0,
    total_paise      BIGINT   NOT NULL,
    operator_name    TEXT,
    created_at       TEXT     NOT NULL,
    updated_at       TEXT     NOT NULL
);

CREATE UNIQUE INDEX idx_sale_bill_no ON sale (bill_no);
CREATE INDEX idx_sale_created_at ON sale (created_at);

CREATE TABLE sale_line (
    id               CHAR(36) PRIMARY KEY,
    sale_id          CHAR(36) NOT NULL REFERENCES sale (id),
    product_id       CHAR(36) NOT NULL REFERENCES product (id),
    -- Snapshotted at completion, so a later reprice/rename never alters this bill.
    name             TEXT     NOT NULL,
    barcode          TEXT,
    mrp_paise        BIGINT   NOT NULL,
    unit_price_paise BIGINT   NOT NULL,
    quantity         BIGINT   NOT NULL CHECK (quantity > 0),
    line_total_paise BIGINT   NOT NULL,
    saving_paise     BIGINT   NOT NULL,
    created_at       TEXT     NOT NULL,
    updated_at       TEXT     NOT NULL
);

CREATE INDEX idx_sale_line_sale ON sale_line (sale_id);
