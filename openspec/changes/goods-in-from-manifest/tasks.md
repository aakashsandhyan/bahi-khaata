## 1. Schema for expectation, boxes and lot state

- [x] 1.1 Write the V12 migration creating `EXPECTED_LINE` (UUID PK, `lot_id`, `product_id`, supplier `code`, `quantity_expected` BIGINT, `stated_value_paise` BIGINT nullable, `tracking_number` text, timestamps) with a foreign key to `lot` and `product`. Review the SQL before any entity exists.
- [x] 1.2 Extend V12 with `BOX` (UUID PK, `lot_id`, `tracking_number`, `finished_at` nullable, timestamps) and a unique constraint on `(lot_id, tracking_number)`. A box is per-lot because one tracking number carries goods from a single category sheet.
- [x] 1.3 Extend V12 with `lot.state` (`OPEN`/`CLOSED`, CHECK-constrained, defaulting to `OPEN`) and `lot.closed_at` nullable. Add `AllocationState` or reuse a plain enum in contracts, and add the drift-test row so the enum and the CHECK cannot diverge.
- [x] 1.4 Make `batch.allocated_total_paise`, `batch.allocated_unit_cost_paise` and `batch.cost_basis` nullable in V12, since a batch now carries stock before its lot is closed. Verify under `ddl-auto=validate` that Hibernate still starts.
- [x] 1.5 Add `ESTIMATED` to `CostBasis` and its CHECK constraint, for lines weighted from the lot average rather than from a stated value, and update the drift test.
- [x] 1.6 Map `ExpectedLine` and `Box` entities following the existing converter and declared-type conventions, and test that both round-trip.
- [x] 1.7 Test at the database level that an uncosted batch is distinguishable from a zero-cost one: a batch with null allocated cost reads back as null, not as `Money.ZERO`.

## 2. Manifest import becomes an expectation

- [x] 2.1 Extend `tools/consignment.py` to read the `Tracking number` column it currently discards, keyed per line, and confirm against the real workbook that 533 distinct boxes are found across 3,583 units.
- [x] 2.2 Extend `ImportLine` in contracts with the tracking number, and `ImportResult` with counts of boxes and expected lines recorded.
- [x] 2.3 Rewrite `ConsignmentImporter` to write the lot, its boxes, its expected lines, and the products and barcodes — and no stock ledger entries at all. Keep the whole import in one transaction.
- [x] 2.4 Test that importing a manifest writes no ledger entry and leaves stock on hand unchanged for every product it names.
- [x] 2.5 Test that a product named by the manifest is created with no stock, that an already-known code is matched rather than duplicated, and that a never-counted line leaves a catalogue entry rather than an error.
- [x] 2.6 Test that an unmapped supplier product-line code fails the import reporting that code, and that nothing from the manifest is recorded.
- [x] 2.7 Keep the existing online-price behaviour under the new shape: recorded only from off-market manifests, averaged by quantity across repeated rows, refused when two marketplaces disagree. Re-run the existing tests against the rewritten importer.
- [x] 2.8 Re-import the Sushil consignment against a fresh database and verify 7 lots, 533 boxes, 1,878 products, 1,878 expected lines totalling 3,583 expected units, and **zero** units on hand.

## 3. Counting what actually arrived

- [x] 3.1 Add `GoodsInCounting` in `backend`, with an operation recording a counted quantity for one expected line: it creates or updates the batch, writes the receipt to the ledger, and leaves the allocated cost null.
- [x] 3.2 Test the three count outcomes against one expected line of twelve: counting twelve, counting eleven, and counting thirteen. In each case stock on hand equals the counted quantity, and the difference from expected is readable.
- [x] 3.3 Implement recording goods found in a box that no expected line names, against the lot, distinguishable from expected goods. Test that they reach the ledger and are identifiable as unlisted.
- [x] 3.4 Implement per-line count persistence so a part-counted box retains its counts, and test that reopening it reports only the lines still to count.
- [x] 3.5 Implement marking a box finished, and test that a finished box keeps any shortfall or surplus visible rather than clearing it.
- [x] 3.6 Implement the per-lot completeness report: boxes not started, part counted, and finished. Test each state, including that a box with a single line behaves identically to one with many.
- [x] 3.7 Add the counting endpoints to `ConsignmentController` — record a count, record an unlisted item, finish a box, read a lot's completeness — and verify them against a booted backend with `curl`, not only by MockMvc.

## 4. Closing a lot and apportioning its cost

- [x] 4.1 Change `CostAllocator`'s caller to allocate over counted quantities rather than expected ones. The allocator itself already takes lines as an input and needs no change.
- [x] 4.2 Implement lot closing: apportion the amount paid across the batches actually received, write the allocated totals, unit costs and cost bases, and set the lot to `CLOSED`. One transaction.
- [x] 4.3 Test that closing apportions exactly: the shares sum to the amount paid, to the paise, across a lot with mixed quantities and values.
- [x] 4.4 Test that a short line raises the per-unit cost of the units that did arrive, and that expected lines never counted receive no share at all.
- [x] 4.5 Implement weighting unlisted goods at the average per-unit stated value of the lot's named lines, recording the basis as `ESTIMATED`. Test the average is computed over named lines only.
- [x] 4.6 Test that closing a lot in which no line carries a stated value is refused, reporting that a value must be supplied, and that nothing is apportioned.
- [x] 4.7 Implement closing over unopened boxes: report the boxes concerned and proceed only on explicit confirmation. Test that unconfirmed closing is refused and confirmed closing succeeds.
- [x] 4.8 Implement refusal of counts against a closed lot, and test that stock recorded before closing keeps its apportioned cost.
- [x] 4.9 Test that per-unit cost tracks per-unit value and not quantity, against the closing path rather than the allocator directly — the fault this guards was invisible to lot totals.
- [x] 4.10 Implement point-in-time valuation reporting uncosted stock separately rather than valuing it at zero, and test a valuation taken while a lot is still open.

## 5. Shelf readiness

- [x] 5.1 Add the labelled state to `product` or `batch` as the design requires, with the migration reviewed before the entity.
- [x] 5.2 Implement the sellability gate as price plus recorded MRP plus label, and make the missing element reportable. Extend the existing sellability tests rather than replacing them.
- [x] 5.3 Refuse setting a price while the product's lot is open, reporting that the cost is not yet known, and test both sides of closing.
- [x] 5.4 Refuse a selling price above the recorded MRP, and test the boundary exactly at the MRP.
- [x] 5.5 Implement MRP capture against the batch during counting, including marking a looked-up figure as an estimate, and test that a batch without an MRP holds its product off the floor.
- [x] 5.6 Implement label content — MRP, selling price, and the saving in both rupees and percent — as a rendered model, without touching printer hardware. Test the saving arithmetic, including the rounding of the percentage.
- [x] 5.7 Refuse producing a label without a recorded MRP, and test it.
- [x] 5.8 Implement resolving a product's origin: its batches, their lots, and the suppliers. Test that a scanned label reaches the supplier, and that with two batches on hand the batch is attributed by consumption order rather than determined from the code.

## 6. The unpacking screen

- [x] 6.1 Add the terminal's screen scaffolding — navigation, a scan input that holds focus, and the Devanagari-capable font already bundled. No unpacking logic yet.
- [x] 6.2 Extend `BackendClient` with the counting operations from 3.7, each failing loudly and legibly when the backend is unreachable.
- [x] 6.3 Build the box screen: scan or choose a tracking number, then show what is expected in it. Build for the median box of one line, and confirm the one-line case is two scans and no typing.
- [x] 6.4 Implement scanning an item within a box: match it to an expected line, take a count where it is not one, and take the MRP read off the pack.
- [x] 6.5 Implement the unmatched-scan state — an item that is not on the sheet — visibly distinct and recordable as unlisted without leaving the screen.
- [x] 6.6 Implement finishing a box, showing what was short or surplus before it is marked finished.
- [x] 6.7 Implement resuming: reopening a part-counted box shows what remains, and closing the application mid-box loses nothing.
- [x] 6.8 Implement the lot view: which boxes remain, and closing the lot with the confirmation from 4.7 when boxes are unopened.
- [x] 6.9 Review every string on the screen against the design's vocabulary rule — no batch, lot, allocation, FIFO or ledger — and check the finished and unmatched states are legible from across a room.
- [x] 6.10 Walk the whole path against real data on a booted backend: import, open a box, count short, count a surplus, finish, close the lot, and confirm the costs land where section 4 says they should.

## 7. Superseding the one-act path

- [x] 7.1 Mark task 4.13 in `foundation-and-first-sale` superseded by section 6, with a line saying why, so the older goods-in screen is not built against a replaced model.
- [x] 7.2 Keep `GoodsInService.receive()` for small hand-entered deliveries and document in its Javadoc that consignments go through counting instead. Confirm its tests still pass unchanged.
- [x] 7.3 Update `Bachat_Bazar_POS_Report.md` and the change's own design if anything drifted during implementation, so the record matches what was built.

## 8. Cost pinned at receipt (overturns section 4 — apportion-at-close)

- [ ] 8.1 `Batch`: add a costed-at-receipt path that sets `allocatedUnitCost`, `costBasis = PINNED`, and `allocatedTotal` when a batch is created with a known per-unit cost. Keep the uncosted path for a surplus.
- [ ] 8.2 `countExpected` pins the expected line's stated per-unit cost onto the batch it creates (`CostBasis.PINNED`). Test the batch is `isCosted()` at receipt, no lot close needed.
- [ ] 8.3 `countUnlisted` (surplus, no stated cost) leaves the batch uncosted. Test `isCosted()` is false and distinguishable from a zero cost.
- [ ] 8.4 `ConsignmentImporter` reads the manifest's per-product cost into the expected line, so counting can pin it. Test import populates it; extend `tools/consignment.py` to read the cost column.
- [ ] 8.5 `LotClosing`: closing marks the lot `CLOSED` (receiving done) without apportioning — remove the `CostAllocator` call, keep the unopened-boxes confirmation. Test close changes no batch's cost.
- [ ] 8.6 Remove the `CostBasis.ESTIMATED` lot-average weighting of a surplus; a surplus stays uncosted.
- [ ] 8.7 Amount-paid cross-check: sum of pinned costs × received quantity vs amount paid; report a material mismatch. Test clean and mismatch.
- [ ] 8.8 Update the section-4 tests (apportion-at-close, uncosted-until-close, lot-average) to the pinned model; confirm the pre-existing "boxes pending / can't price an open lot" failures clear.
- [ ] 8.9 Frontend: the manual add-product path pins its entered per-unit cost; manifest import pins per line.
- [ ] 8.10 Verify: boots under `ddl-auto=validate`; full suite green; price a real product from an open lot and see the margin suggestion fire.
