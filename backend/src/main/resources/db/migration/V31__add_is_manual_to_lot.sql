-- Add is_manual column to lot table for manual (manifest-free) lots.
-- Manual lots are created without a packing list; products are entered manually.

ALTER TABLE lot ADD COLUMN is_manual BOOLEAN NOT NULL DEFAULT false;
