-- V53 — cart continuity (cart-continuity change).
--
-- A cart persists until paid, cleared, or the morning sweep; it can be held and resumed from any
-- register. It therefore carries its customer (so a hold keeps the person — completing copies
-- this onto the sale) and the register it was opened on (the carts panel's chip). `touched_at`
-- is bumped by every mutation and is the panel's sort key and the sweep's clock — updated_at
-- alone would lie, since line changes never touch the cart row.
--
-- Additive only; existing carts get their updated_at as the initial touch.

ALTER TABLE cart ADD COLUMN customer_id CHAR(36) REFERENCES customer (id);
ALTER TABLE cart ADD COLUMN register_name TEXT;
ALTER TABLE cart ADD COLUMN touched_at TEXT;
UPDATE cart SET touched_at = updated_at;
CREATE INDEX idx_cart_state_touched ON cart (state, touched_at);
