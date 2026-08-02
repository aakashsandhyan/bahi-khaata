-- V43 — the receipt printer's configuration and the bill's settings.
--
-- The bill prints on a second physical printer (an 80mm ESC/POS receipt printer), configured
-- independently of the label printer. `bill_settings` holds the editable header/footer text so the
-- shop's tax treatment — a composition Bill of Supply for now, pending the CA — is a settings
-- change, not code. Both are singletons with a fixed id, seeded with defaults.

CREATE TABLE receipt_printer_config (
    id              CHAR(36)     PRIMARY KEY,
    address         VARCHAR(255) NOT NULL DEFAULT '',
    transport       VARCHAR(20)  NOT NULL DEFAULT 'LAN' CHECK (transport IN ('LAN', 'USB')),
    enabled         BOOLEAN      NOT NULL DEFAULT 0,
    test_status     VARCHAR(50)  CHECK (test_status IN ('OK', 'UNREACHABLE', 'ERROR')),
    test_error      TEXT,
    last_tested_at  TEXT,
    created_at      TEXT         NOT NULL,
    updated_at      TEXT         NOT NULL
);

INSERT INTO receipt_printer_config (id, address, transport, enabled, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000002', '', 'LAN', 0,
     '2026-08-02T00:00:00.000Z', '2026-08-02T00:00:00.000Z');

CREATE TABLE bill_settings (
    id            CHAR(36) PRIMARY KEY,
    shop_name     TEXT     NOT NULL DEFAULT 'Bachat Bazaar',
    address       TEXT     NOT NULL DEFAULT '',
    gstin         TEXT     NOT NULL DEFAULT '',
    bill_title    TEXT     NOT NULL DEFAULT 'Bill of Supply',
    declaration   TEXT     NOT NULL DEFAULT 'Composition taxable person, not eligible to collect tax on supplies',
    footer        TEXT     NOT NULL DEFAULT 'Thank you!',
    created_at    TEXT     NOT NULL,
    updated_at    TEXT     NOT NULL
);

INSERT INTO bill_settings (id, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000003',
     '2026-08-02T00:00:00.000Z', '2026-08-02T00:00:00.000Z');
