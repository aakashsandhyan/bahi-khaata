-- V27 — remember when a box was last worked, so the screen can offer the boxes in hand.
--
-- Opening a box records nothing: the work is the counting. So the signal for "recently opened" is
-- the last count, extra, finish, or reopen against the box, stamped by the counting flow into this
-- column. A box nobody has touched stays null and does not show up as recent — being created when
-- the manifest loaded is not the same as being worked.
--
-- Existing boxes are seeded from the best evidence already recorded: the latest of any line update
-- (a count bumps it), any extra added, the finish time, and — so every box gets some value — when
-- the box was created. MAX ignores nulls, and created_at is always present.

ALTER TABLE box ADD COLUMN last_activity_at TEXT;

UPDATE box SET last_activity_at = (
    SELECT MAX(t) FROM (
        SELECT MAX(el.updated_at) AS t FROM expected_line el WHERE el.box_id = box.id
        UNION ALL
        SELECT MAX(uf.created_at) FROM unlisted_find uf WHERE uf.box_id = box.id
        UNION ALL
        SELECT box.finished_at
        UNION ALL
        SELECT box.created_at
    )
);
