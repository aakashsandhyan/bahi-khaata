-- V39 — widen print_job for the label review queue.
--
-- The review queue reuses print_job with a new status 'review': pricing enqueues one entry per
-- product (the whole command reviewed in one go), a reviewer edits it, and on send it explodes into
-- its `copies` single-label 'queued' jobs. A review entry's `copies` is the on-hand count, which can
-- exceed the old 1..100 per-label cap, and a reviewer may set it to 0 to skip printing.
--
-- SQLite cannot alter a CHECK constraint in place, so the table is rebuilt with the wider
-- constraints (status gains 'review'; copies allows 0 and a generous upper bound), preserving every
-- existing row.

ALTER TABLE print_job RENAME TO print_job_old;

CREATE TABLE print_job (
    id                  CHAR(36)    PRIMARY KEY,
    barcode             TEXT        NOT NULL,
    product_name        TEXT        NOT NULL,
    selling_price_paise BIGINT      NOT NULL,
    mrp_paise           BIGINT,
    copies              INTEGER     NOT NULL CHECK (copies >= 0 AND copies <= 100000),
    product_id          CHAR(36),
    status              VARCHAR(50) NOT NULL CHECK (status IN ('queued', 'printing', 'done', 'failed', 'review')),
    error               TEXT,
    retry_count         INTEGER     NOT NULL DEFAULT 0,
    created_at          TEXT        NOT NULL,
    updated_at          TEXT        NOT NULL
);

INSERT INTO print_job (
    id, barcode, product_name, selling_price_paise, mrp_paise, copies,
    product_id, status, error, retry_count, created_at, updated_at)
SELECT
    id, barcode, product_name, selling_price_paise, mrp_paise, copies,
    product_id, status, error, retry_count, created_at, updated_at
FROM print_job_old;

DROP TABLE print_job_old;

CREATE INDEX idx_print_job_status ON print_job (status);
CREATE INDEX idx_print_job_created_at ON print_job (created_at);
