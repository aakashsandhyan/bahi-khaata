-- Probes what declared column type Hibernate expects for a UUID-typed field
-- stored as readable text. Design decision 7 wants 36-character TEXT so that
-- identifiers stay legible in a manual sqlite3 session.

CREATE TABLE uuid_probe (
    id    CHAR(36) PRIMARY KEY,
    label TEXT     NOT NULL
);
