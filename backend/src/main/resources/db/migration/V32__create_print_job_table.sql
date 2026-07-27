-- V32 — Print job queue for barcode label printing on TSC TE-244.
--
-- Each print request (box, batch, or product) is queued as a job. The executor polls
-- the queue every 500ms, renders the label via FreeMarker + ZPL, and sends to the printer
-- (USB or network). Retry logic handles offline printers: queued jobs retry 10 times
-- (~5 seconds), then fail with an error message.
--
-- Status: queued → printing → done (or failed after retries).
-- Error field stores the failure reason (printer unreachable, rendering error, etc.).

CREATE TABLE print_job (
    id          CHAR(36)    PRIMARY KEY,
    item_type   VARCHAR(50) NOT NULL CHECK (item_type IN ('box', 'batch', 'product')),
    item_id     CHAR(36)    NOT NULL,
    copies      INTEGER     NOT NULL CHECK (copies > 0 AND copies <= 100),
    status      VARCHAR(50) NOT NULL CHECK (status IN ('queued', 'printing', 'done', 'failed')),
    error       TEXT,
    retry_count INTEGER     NOT NULL DEFAULT 0,
    created_at  TEXT        NOT NULL,
    updated_at  TEXT        NOT NULL
);

CREATE INDEX idx_print_job_status ON print_job (status);
CREATE INDEX idx_print_job_created_at ON print_job (created_at);
