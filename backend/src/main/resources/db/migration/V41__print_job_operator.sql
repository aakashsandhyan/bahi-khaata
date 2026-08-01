-- V41 — record which operator priced a review entry.
--
-- The review screen shows who priced each waiting item. There is no login, so the pricing screen
-- carries the operator's name (remembered per device) and it is stored on the entry. Nullable —
-- older rows and the exploded print jobs have none. Adding a column is allowed in place.

ALTER TABLE print_job ADD COLUMN operator_name TEXT;
