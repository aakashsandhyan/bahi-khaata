-- V12 — receiving becomes two acts: what the supplier claims, and what we actually found.
--
-- Until now a manifest was recorded as fact. The first real consignment showed why that is
-- wrong: 3,583 units went on hand on a supplier's word with no box opened. These are
-- liquidation and returns goods, where the manifest and the delivery routinely disagree, and
-- the difference between them is the thing worth recording.
--
-- So an import now writes an expectation — a lot, its boxes, and the lines each box should
-- contain — and no stock at all. Stock arrives when someone opens a box and counts.
--
-- Three consequences shape the schema below:
--
--   1. A batch is created at counting time and carries stock before anyone knows what it
--      cost, because a lot's shares cannot be settled until every box is opened. Its cost
--      columns therefore become nullable, and null means "not yet apportioned" — which is a
--      different thing from a cost of zero and must never be read as one.
--
--   2. Cost is apportioned when the lot closes, over what actually arrived. So a lot needs a
--      state: open while boxes are still being counted, closed once its money has been split.
--
--   3. Goods sometimes turn up that no line names. They still take a share of what was paid,
--      weighted at the lot's average unit value, and ESTIMATED records that the weight was
--      derived rather than stated.

-- Every expected line names the box it should arrive in. The tracking number was in every
-- supplier sheet from the beginning and the importer discarded it; without it there is no way
-- to answer which cartons are still unopened.
CREATE TABLE box (
    id             CHAR(36) PRIMARY KEY,
    lot_id         CHAR(36) NOT NULL REFERENCES lot (id),

    -- The carrier's number, printed on the carton. Scanning it is how unpacking starts.
    tracking_number TEXT    NOT NULL,

    -- Set when someone says they have finished with this box. Null means not finished, which
    -- covers both "not started" and "part counted" — the difference between those two is a
    -- question about counts, not about the box, so it is derived rather than stored.
    finished_at    TEXT,

    created_at     TEXT     NOT NULL,
    updated_at     TEXT     NOT NULL,

    -- Per lot, because one tracking number carries goods from a single category sheet. The
    -- same number could in principle recur across suppliers, so it is not globally unique.
    UNIQUE (lot_id, tracking_number)
);

-- What the supplier says is coming. Deliberately separate from `batch`: a batch is stock we
-- hold, an expected line is a claim we have not yet tested. Keeping the claim after counting
-- is the point — it is what a shortfall is measured against.
CREATE TABLE expected_line (
    id                 CHAR(36) PRIMARY KEY,
    lot_id             CHAR(36) NOT NULL REFERENCES lot (id),
    box_id             CHAR(36) NOT NULL REFERENCES box (id),

    -- The catalogue entry created at import. The product exists before any of it is held;
    -- that is what lets the unpacking screen match a scan to a name.
    product_id         CHAR(36) NOT NULL REFERENCES product (id),

    -- The supplier's own code for the line, kept as stated. Also the product's barcode, but
    -- recorded here too so the manifest remains readable on its own terms.
    code               TEXT     NOT NULL,

    quantity_expected  BIGINT   NOT NULL CHECK (quantity_expected > 0),

    -- What the line is worth per unit, used to weigh its share of the lot. A marketplace
    -- selling price on a returns sheet, a supplier cost on a cost-plus one. Nullable because
    -- a manifest may state neither, in which case the line is weighed at the lot average.
    --
    -- Per unit, not a line total. Quantity is applied by the allocation itself, and supplying
    -- a total here would apply it twice — which inflates multi-unit lines and squeezes
    -- single-unit ones while leaving the lot total looking correct.
    stated_value_paise BIGINT   CHECK (stated_value_paise IS NULL OR stated_value_paise > 0),

    created_at         TEXT     NOT NULL,
    updated_at         TEXT     NOT NULL,

    -- One line per product per box. A manifest lists rows, not products, and the same code
    -- appears many times in a box; those rows are combined at import.
    UNIQUE (box_id, product_id)
);

CREATE INDEX idx_expected_line_lot ON expected_line (lot_id);
CREATE INDEX idx_box_lot ON box (lot_id);

-- A lot is open while its boxes are being counted and closed once its cost has been split
-- across what arrived. Closing is one-way: costs from a closed lot may already have been used
-- to set prices, so reopening it quietly would leave those prices resting on figures that no
-- longer exist.
--
-- Added with a default so existing lots — which were costed under the old one-act import —
-- read as closed, which is what they are.
ALTER TABLE lot ADD COLUMN state TEXT NOT NULL DEFAULT 'CLOSED'
    CHECK (state IN ('OPEN', 'CLOSED'));

ALTER TABLE lot ADD COLUMN closed_at TEXT;
