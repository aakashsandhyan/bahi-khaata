-- V6 — the stock ledger.
--
-- Append-only, and the source of truth for stock. Quantity on hand is derived from these
-- rows rather than held as a counter, so no stored total can silently disagree with the
-- movements that produced it.
--
-- Immutable from creation, enforced at the database as well as in the entity: the specs
-- require that a direct sqlite3 session cannot rewrite history, and an ORM annotation
-- cannot deliver that. Pattern per docs/immutability-triggers.md.
--
-- Deliberately no invoice_id yet. The invoice table does not exist until the invoicing
-- migration, and a foreign key cannot point at a missing table. It is added there with
-- ALTER TABLE ... ADD COLUMN ... REFERENCES invoice (id), which SQLite permits for a
-- nullable column and which leaves these triggers intact.

CREATE TABLE stock_ledger (
    id            CHAR(36) PRIMARY KEY,
    product_id    CHAR(36) NOT NULL REFERENCES product (id),
    batch_id      CHAR(36) NOT NULL REFERENCES batch (id),

    -- Signed: positive is stock arriving, negative is stock leaving. Direction lives in
    -- the sign rather than a separate column, so no row can record a direction that
    -- contradicts its quantity. Zero is not a movement.
    quantity      BIGINT   NOT NULL CHECK (quantity <> 0),

    -- Why the stock moved. Reconciling a discrepancy means telling a sale apart from a
    -- write-off, which a bare in/out flag would discard.
    movement_type TEXT     NOT NULL
        CHECK (movement_type IN ('PURCHASE_RECEIPT', 'SALE', 'WRITE_OFF', 'ADJUSTMENT')),

    -- Cost of goods sold, at the consumed batch's cost. Only ever on stock leaving:
    -- an arrival has no COGS.
    cogs_paise    BIGINT   CHECK (cogs_paise IS NULL OR quantity < 0),

    -- When the movement actually happened, in business terms — not when the row was
    -- written. A delivery logged two days late is appended with its true effective time,
    -- and derived figures recalculate forward from there.
    effective_at  TEXT     NOT NULL,

    -- When the row was appended. Immutable rows have no updated_at: there is nothing to
    -- track, because nothing may change.
    created_at    TEXT     NOT NULL
);

-- On-hand and valuation read a product's movements in effective order; FIFO reads what
-- remains of a given batch.
CREATE INDEX idx_ledger_product_effective ON stock_ledger (product_id, effective_at);
CREATE INDEX idx_ledger_batch ON stock_ledger (batch_id);

-- Append-only, enforced by the database itself.
CREATE TRIGGER stock_ledger_no_update
BEFORE UPDATE ON stock_ledger
BEGIN
    SELECT RAISE(ABORT, 'stock_ledger is append-only: rows cannot be updated');
END;

CREATE TRIGGER stock_ledger_no_delete
BEFORE DELETE ON stock_ledger
BEGIN
    SELECT RAISE(ABORT, 'stock_ledger is append-only: rows cannot be deleted');
END;
