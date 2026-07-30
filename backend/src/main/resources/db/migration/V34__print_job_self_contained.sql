-- V34 — Recreate print_job as a self-contained label job.
--
-- The label print queue no longer looks anything up: a job carries the label fields it needs
-- (barcode, product name, selling price, optional MRP), and the executor renders straight from
-- them without a database read. A later price change therefore cannot alter a queued label, and
-- a reprint simply queues fresh values.
--
-- product_id is a back-reference only: on a successful print the executor marks that product's
-- label as printed, and the bulk screen uses it to tell what is done. It is never read to render.
--
-- The previous item_type/item_id shape is dropped. There are no production print_job rows (the
-- executor's data path was never finished — it threw "not yet implemented"), so the table is
-- recreated rather than migrated column by column.

DROP TABLE IF EXISTS print_job;

CREATE TABLE print_job (
    id                  CHAR(36)    PRIMARY KEY,
    barcode             TEXT        NOT NULL,
    product_name        TEXT        NOT NULL,
    selling_price_paise BIGINT      NOT NULL,
    mrp_paise           BIGINT,
    copies              INTEGER     NOT NULL CHECK (copies > 0 AND copies <= 100),
    product_id          CHAR(36),
    status              VARCHAR(50) NOT NULL CHECK (status IN ('queued', 'printing', 'done', 'failed')),
    error               TEXT,
    retry_count         INTEGER     NOT NULL DEFAULT 0,
    created_at          TEXT        NOT NULL,
    updated_at          TEXT        NOT NULL
);

CREATE INDEX idx_print_job_status ON print_job (status);
CREATE INDEX idx_print_job_created_at ON print_job (created_at);
