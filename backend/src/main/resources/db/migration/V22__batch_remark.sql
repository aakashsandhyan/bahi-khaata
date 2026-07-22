-- V22 — a note on why goods are damaged.
--
-- When an item is marked damaged or broken, someone can say what is wrong with it — "lid
-- cracked", "box opened, contents fine", "screen dead". The manager pricing it later, or
-- writing it off, needs that reason, and it lives nowhere else: the condition says damaged, this
-- says how.
--
-- On the batch, since that is where a condition already lives, and one product's damaged units
-- in one delivery are the one batch. Nullable, and only ever set for goods that are not sound —
-- there is nothing to remark on an item in good order.

ALTER TABLE batch ADD COLUMN remark TEXT;
