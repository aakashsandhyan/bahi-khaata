-- Backfill box_receipt for existing closed lots
-- For existing lots, since they're already closed, we assume receiving is complete
-- so set receiving_complete = true. Box-level tracking is not retroactively populated
-- since we don't have manifest carton IDs in older records.

UPDATE lot SET receiving_complete = TRUE WHERE state = 'CLOSED';
