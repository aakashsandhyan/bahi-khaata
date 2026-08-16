-- V52 — customer capture (customer-capture change).
--
-- The counter asks every customer for name + mobile; the ones who decline are walk-ins. The
-- mobile is the identity: normalized to ten digits and unique, one customer per number. A sale
-- optionally references its customer — null is a walk-in and stays valid forever, as does all
-- history from before capture existed. Additive only: no rebuilds, rollback leaves the columns
-- inert.
--
-- Stats (visits, spend, repeat share, lapsed) are DERIVED from customer × sale at read time —
-- deliberately no counter columns here, stored aggregates drift.

CREATE TABLE customer (
    id         CHAR(36) PRIMARY KEY,
    name       TEXT     NOT NULL,
    -- Normalized: exactly ten digits, no country code, no punctuation (enforced in the service).
    mobile     TEXT     NOT NULL UNIQUE,
    created_at TEXT     NOT NULL,
    updated_at TEXT     NOT NULL
);

ALTER TABLE sale ADD COLUMN customer_id CHAR(36) REFERENCES customer (id);
CREATE INDEX idx_sale_customer ON sale (customer_id);
