-- V36 — Mobile capture queue.
--
-- A capture is a pricing-free draft keyed from a phone on the shop network: a name, and any of
-- an optional MRP, description, or lot. It is not a product and never reaches the shelf on its
-- own — a reviewer on the desktop completes lot/category/price and approves it, which creates
-- the product and marks the capture approved.
--
-- lot_id is nullable: the phone may know the lot, or the reviewer assigns it. No foreign key to
-- lot is declared, matching the loose coupling the rest of the schema keeps between draft-stage
-- rows and inventory (a capture may name a lot that is later corrected at review).

CREATE TABLE product_capture (
    id          CHAR(36)    PRIMARY KEY,
    name        TEXT        NOT NULL,
    mrp_paise   BIGINT,
    description TEXT,
    lot_id      CHAR(36),
    status      VARCHAR(20) NOT NULL CHECK (status IN ('pending', 'approved', 'rejected')),
    created_at  TEXT        NOT NULL,
    updated_at  TEXT        NOT NULL
);

CREATE INDEX idx_product_capture_status_created ON product_capture (status, created_at);
