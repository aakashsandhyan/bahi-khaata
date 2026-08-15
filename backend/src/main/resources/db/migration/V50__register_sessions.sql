-- V50 — register drawer sessions (palletworks-selling).
--
-- A session is the accountable unit for a physical drawer: opened with a counted float by a named
-- operator, taking cash movements while open, and closed against a counted drawer. `over_short_paise`
-- is computed at close (counted − (float + cash sales + in − out)) and PINNED — like a bill, the
-- figure must survive later data corrections, so it is stored, not derived.
--
-- A sale rung while its register has an open session references it; a sale with no session (the
-- classic till, or all history before this migration) stays valid with NULL forever.

CREATE TABLE register_session (
    id               CHAR(36) PRIMARY KEY,
    register_name    TEXT     NOT NULL,
    operator_name    TEXT     NOT NULL,
    float_paise      BIGINT   NOT NULL CHECK (float_paise >= 0),
    status           TEXT     NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    opened_at        TEXT     NOT NULL,
    closed_at        TEXT,
    counted_paise    BIGINT,
    over_short_paise BIGINT,
    created_at       TEXT     NOT NULL,
    updated_at       TEXT     NOT NULL
);

-- One open session per register, enforced structurally: the partial index admits at most one OPEN
-- row per register name, so a double-open loses the race at the database, not just in the service.
CREATE UNIQUE INDEX idx_register_session_one_open
    ON register_session (register_name) WHERE status = 'OPEN';
CREATE INDEX idx_register_session_name ON register_session (register_name);

CREATE TABLE register_cash_movement (
    id            CHAR(36) PRIMARY KEY,
    session_id    CHAR(36) NOT NULL REFERENCES register_session (id),
    direction     TEXT     NOT NULL CHECK (direction IN ('IN', 'OUT')),
    amount_paise  BIGINT   NOT NULL CHECK (amount_paise > 0),
    note          TEXT,
    created_at    TEXT     NOT NULL,
    updated_at    TEXT     NOT NULL
);

CREATE INDEX idx_register_cash_movement_session ON register_cash_movement (session_id);

ALTER TABLE sale ADD COLUMN register_session_id CHAR(36) REFERENCES register_session (id);
CREATE INDEX idx_sale_register_session ON sale (register_session_id);
