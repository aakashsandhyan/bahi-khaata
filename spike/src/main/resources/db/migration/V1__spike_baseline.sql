-- Spike baseline. Flyway applies this; Hibernate then validates its entity
-- mappings against it with ddl-auto=validate and refuses to start on mismatch.
--
-- Two declaration rules the validator enforces, both discovered here rather
-- than during schema work. SQLite ignores both distinctions at runtime — it
-- has one INTEGER type and TEXT affinity covers CLOB — but Hibernate compares
-- declared types, so the DDL has to match what the dialect expects:
--
--   * a column backing a Java `long` must be declared BIGINT, not INTEGER.
--     Every paise column in the real schema is a `long`.
--   * a column backing @JdbcTypeCode(SqlTypes.JSON) must be declared CLOB,
--     not TEXT.

CREATE TABLE widget (
    id          TEXT   PRIMARY KEY,
    name        TEXT   NOT NULL,
    attributes  CLOB,
    price_paise BIGINT NOT NULL
);

CREATE TABLE ledger_row (
    id            TEXT   PRIMARY KEY,
    quantity      BIGINT NOT NULL,
    movement_type TEXT   NOT NULL
);
