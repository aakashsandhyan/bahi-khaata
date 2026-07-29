-- V33 — Printer configuration (singleton).
--
-- Admin configures printer address (IP:port for network, /dev/ttyUSB0 for USB),
-- port speed (9600, etc.), and default copies. The config screen includes a test
-- button that checks connectivity and stores the result (OK, UNREACHABLE, ERROR).
--
-- Singleton pattern: exactly one row per installation. ID is a fixed UUID.
-- If no row exists on startup, a default is created.

CREATE TABLE printer_config (
    id              CHAR(36)    PRIMARY KEY,
    address         VARCHAR(255) NOT NULL,
    port_speed      INTEGER     NOT NULL CHECK (port_speed > 0 AND port_speed <= 115200),
    paper_size      VARCHAR(10) NOT NULL DEFAULT '4x6',
    copies_default  INTEGER     NOT NULL DEFAULT 1 CHECK (copies_default > 0 AND copies_default <= 5),
    enabled         BOOLEAN     NOT NULL DEFAULT 1,
    test_status     VARCHAR(50) CHECK (test_status IN ('OK', 'UNREACHABLE', 'ERROR')),
    test_error      TEXT,
    last_tested_at  TEXT,
    created_at      TEXT        NOT NULL,
    updated_at      TEXT        NOT NULL
);

-- Singleton enforcement: only one row allowed (via application logic, not constraint).
CREATE UNIQUE INDEX idx_printer_config_singleton ON printer_config (id);
