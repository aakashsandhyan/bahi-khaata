-- V15 — the label is the gate onto the shop floor.
--
-- Goods reach a customer only once a label is on them, and a label can only be printed when
-- there is both a price and an MRP to print. That makes labelling the last checkable step
-- between a carton and the shelf, and the one worth recording: without it, "is this ready to
-- sell" has no answer beyond someone's memory of whether they ran the labels.
--
-- Held on the batch rather than the product because a label carries the MRP, and MRP is per
-- batch — successive deliveries of the same product genuinely arrive bearing different printed
-- prices. Labelling one delivery says nothing about the next.

ALTER TABLE batch ADD COLUMN labelled_at TEXT;
