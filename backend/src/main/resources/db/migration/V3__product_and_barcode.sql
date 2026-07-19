-- V3 — product catalogue and barcodes.
--
-- Declared-type rules (design decision 3): a Java `long` is BIGINT, a
-- @JdbcTypeCode(SqlTypes.JSON) field is CLOB, a UUID is CHAR(36), a Java `boolean`
-- is BOOLEAN, and plain text columns are declared TEXT (matched by the entity's
-- columnDefinition, so a name is not silently capped at varchar(255)). SQLite
-- ignores these distinctions at runtime; Hibernate's validator does not.
--
-- Timestamps are Instants (UTC, design decision 9), stored as ISO-8601 UTC text
-- via a converter so they stay readable in a sqlite3 session — the same reason
-- ids are CHAR(36). Fixed-width milliseconds keep text sort chronological. The
-- application sets them via @CreationTimestamp / @UpdateTimestamp.

CREATE TABLE product (
    id                    CHAR(36) PRIMARY KEY,
    name                  TEXT     NOT NULL,

    -- Governed set, enforced in the database. Must stay identical to the Category
    -- Java enum; task 2.2 adds a test that fails if the two drift.
    category              TEXT     NOT NULL
        CHECK (category IN ('HOME_ESSENTIALS', 'KITCHEN', 'ELECTRONICS',
                            'GIFTING', 'DECOR', 'FASHION')),

    -- Nullable on purpose: a product exists before anyone has decided its price.
    -- An absent price is NULL, never 0 — see the unpriced-state requirement.
    selling_price_paise   BIGINT,

    -- HSN is not mandatory for B2C below the threshold (report §4.2); stored when
    -- known so a future B2B or threshold crossing is a data question, not a schema one.
    hsn_code              TEXT,

    -- Category-specific attributes as JSON. NULL means none recorded.
    attributes            CLOB,

    price_review_flagged  BOOLEAN   NOT NULL DEFAULT 0,

    -- Product is mutable — name, price, attributes change — so updated_at earns
    -- its place alongside created_at.
    created_at            TEXT      NOT NULL,
    updated_at            TEXT      NOT NULL
);

CREATE TABLE barcode (
    id          CHAR(36) PRIMARY KEY,
    product_id  CHAR(36) NOT NULL REFERENCES product (id),

    -- One code maps to at most one product. UNIQUE both enforces that and provides
    -- the lookup index the checkout hot path needs — no separate index required.
    code        TEXT     NOT NULL UNIQUE,

    -- Governed like category: an internally generated Code 128 must always be
    -- distinguishable from a manufacturer code.
    origin      TEXT     NOT NULL
        CHECK (origin IN ('MANUFACTURER', 'INTERNAL')),

    -- created_at only: a barcode is assigned once and not edited afterwards, so
    -- there is nothing for an updated_at to track.
    created_at  TEXT      NOT NULL
);
