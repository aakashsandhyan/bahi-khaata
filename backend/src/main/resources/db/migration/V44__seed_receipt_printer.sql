-- V44 — seed the shop's receipt printer so the bill prints without a manual config step.
--
-- V43 created the row blank/disabled; this points it at the shop's USB thermal printer as Windows
-- names it. Two reasons to bake it into the DB rather than set it once in the UI: a sandbox
-- re-copies the live DB on each start and would otherwise lose a UI-set config, and go-live needs
-- the printer working out of the box. Shop-specific — if the receipt printer hardware changes, edit
-- it on the Receipt admin screen (which overwrites this) or add a follow-up migration.
--
-- An UPDATE, not an INSERT: the singleton row already exists from V43. Left untouched: test_status
-- and last_tested_at (set only by a real test print).

UPDATE receipt_printer_config
   SET address    = 'TVSE RP3200 Lite',
       transport  = 'USB',
       enabled    = 1,
       updated_at = '2026-08-02T00:00:00.000Z'
 WHERE id = '00000000-0000-0000-0000-000000000002';
