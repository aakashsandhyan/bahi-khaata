-- V37 — Supplier becomes a first-class entity, and every lot points at one.
--
-- Until now a supplier was a free-typed string repeated on each lot, so the same vendor
-- fragmented across casing and spacing and had nowhere to hold a GSTIN, phone, or address.
-- This migration introduces the supplier table, backfills one row per distinct existing
-- lot.supplier string, and links every lot to its new row.
--
-- The legacy lot.supplier text column is deliberately kept, frozen, as a denormalised
-- snapshot. Dropping it is a separate later change once the FK is trusted.
--
-- Normalisation here is lower(trim(...)) — SQLite ships no regexp, so the fuller
-- internal-whitespace collapse that Supplier.normalize() applies to new rows is not mirrored.
-- For the current dataset the two agree except where a name differs only by repeated internal
-- spaces; that lands as two suppliers and is resolved by a manual merge later, not guessed here.

CREATE TABLE supplier (
    id              CHAR(36)    PRIMARY KEY,
    name            TEXT        NOT NULL,
    name_normalized TEXT        NOT NULL,
    gstin           TEXT,
    phone           TEXT,
    address         TEXT,
    contact_person  TEXT,
    notes           TEXT,
    active          BOOLEAN     NOT NULL DEFAULT true,
    created_at      TEXT        NOT NULL,
    updated_at      TEXT        NOT NULL
);

-- Name is the fallback identity for the many vendors with no GSTIN.
CREATE UNIQUE INDEX idx_supplier_name_normalized ON supplier (name_normalized);

-- GSTIN is the legal identity, unique only when present: unregistered vendors leave it null,
-- and a partial index lets any number of null-GSTIN rows coexist while real GSTINs stay unique.
CREATE UNIQUE INDEX idx_supplier_gstin ON supplier (gstin) WHERE gstin IS NOT NULL;

-- One supplier per distinct normalised existing lot.supplier string. The id is a v4 UUID
-- assembled from random bytes so it matches the CHAR(36) form every other table uses; the
-- display name keeps a representative original casing (MIN picks one deterministically).
INSERT INTO supplier (id, name, name_normalized, gstin, phone, address, contact_person, notes, active, created_at, updated_at)
SELECT
    lower(
        hex(randomblob(4)) || '-' ||
        hex(randomblob(2)) || '-4' ||
        substr(hex(randomblob(2)), 2) || '-' ||
        substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' ||
        hex(randomblob(6))
    ),
    MIN(supplier),
    lower(trim(supplier)),
    NULL, NULL, NULL, NULL, NULL,
    true,
    strftime('%Y-%m-%dT%H:%M:%fZ', 'now'),
    strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
FROM lot
GROUP BY lower(trim(supplier));

-- The FK. Nullable at the database because SQLite cannot add a NOT NULL constraint to an
-- existing column without a full table rebuild; non-null is guaranteed by this backfill for
-- existing rows and by the receipt service for new ones, the same app-enforced pattern the
-- lot freeze rule already uses.
ALTER TABLE lot ADD COLUMN supplier_id CHAR(36) REFERENCES supplier (id);

UPDATE lot
SET supplier_id = (
    SELECT s.id FROM supplier s WHERE s.name_normalized = lower(trim(lot.supplier))
);

CREATE INDEX idx_lot_supplier_id ON lot (supplier_id);
