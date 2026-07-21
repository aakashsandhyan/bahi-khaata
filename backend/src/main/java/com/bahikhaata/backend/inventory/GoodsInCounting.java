/*
 * bahi-khaata — point of sale for Bachat Baazar
 * Copyright (C) 2026 Aakash Sandhyan
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.bahikhaata.backend.inventory;

import com.bahikhaata.backend.catalog.Barcode;
import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.Origin;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recording what was actually in the cartons.
 *
 * <p>The second of the two acts. An import says what a supplier claims is coming; this says
 * what someone found when they opened a box, and the ledger believes this one. Where the two
 * disagree, the count is what the shop holds and the expectation stays beside it as the record
 * of what was promised.
 *
 * <p>Counts are written as they happen rather than when a carton is finished. Work stops at
 * closing time, and an interruption must neither discard what has been counted nor force it to
 * be counted again — so a part-counted box is a normal state to walk away from, not an error.
 *
 * <p>Nothing here costs anything. A lot's shares depend on every line in it, so they are
 * settled when the lot closes, over what actually arrived.
 */
@Service
public class GoodsInCounting {

    private final ExpectedLineRepository expectedLines;
    private final UnlistedFindRepository unlistedFinds;
    private final BatchRepository batches;
    private final BoxRepository boxes;
    private final StockLedgerRepository ledger;
    private final ProductRepository products;
    private final BarcodeRepository barcodes;

    GoodsInCounting(
            ExpectedLineRepository expectedLines,
            UnlistedFindRepository unlistedFinds,
            BatchRepository batches,
            BoxRepository boxes,
            StockLedgerRepository ledger,
            ProductRepository products,
            BarcodeRepository barcodes) {
        this.expectedLines = expectedLines;
        this.unlistedFinds = unlistedFinds;
        this.batches = batches;
        this.boxes = boxes;
        this.ledger = ledger;
        this.products = products;
        this.barcodes = barcodes;
    }

    /**
     * Records units found against a line the manifest named.
     *
     * <p>Counting fewer than expected is not an error and neither is counting more. Both are
     * facts about the delivery, and a system that refused either would force the operator to
     * record something untrue.
     */
    @Transactional
    public CountOutcome countExpected(
            UUID expectedLineId, long quantity, Money mrp, boolean mrpIsEstimate, Instant at) {
        ExpectedLine line =
                expectedLines
                        .findById(expectedLineId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "no such expected line: " + expectedLineId));
        requireOpen(line.getLot());

        line.recordCounted(quantity);
        Batch batch = addToBatch(line.getLot(), line.getProduct(), quantity, mrp, mrpIsEstimate, at);

        return new CountOutcome(
                batch.getId(),
                line.getQuantityExpected(),
                line.getQuantityCounted(),
                line.getDiscrepancy());
    }

    /**
     * Records goods found in a carton that no line names.
     *
     * <p>The code is resolved to a product where it is already known and a catalogue entry is
     * created where it is not, so the operator is never blocked by a scan the system has not
     * seen before. Naming it is their job; deciding what it costs is not.
     */
    @Transactional
    public CountOutcome countUnlisted(
            UUID boxId,
            String code,
            String name,
            String categoryCode,
            long quantity,
            Money mrp,
            boolean mrpIsEstimate,
            Instant at) {
        Box box = boxes.findById(boxId)
                .orElseThrow(() -> new IllegalArgumentException("no such box: " + boxId));
        requireOpen(box.getLot());

        Product product = resolveOrCreate(code, name, categoryCode);

        // A second sighting in the same carton adds to the first rather than replacing it.
        UnlistedFind find =
                unlistedFinds.findByBoxIdAndProductId(boxId, product.getId()).orElse(null);
        if (find == null) {
            find = unlistedFinds.save(new UnlistedFind(box.getLot(), box, product, quantity));
        } else {
            find.add(quantity);
        }

        Batch batch = addToBatch(box.getLot(), product, quantity, mrp, mrpIsEstimate, at);

        return new CountOutcome(batch.getId(), 0, find.getQuantity(), find.getQuantity());
    }

    /**
     * Marks a carton finished.
     *
     * <p>Does not require everything expected to have been found. The goods simply may not be
     * there, and refusing to close the box would leave the operator stuck with nothing to do
     * but count air. The shortfall stays visible afterwards.
     */
    @Transactional
    public void finishBox(UUID boxId, Instant at) {
        Box box = boxes.findById(boxId)
                .orElseThrow(() -> new IllegalArgumentException("no such box: " + boxId));
        requireOpen(box.getLot());
        box.finish(at);
    }

    /** Reopens a carton marked finished by mistake. */
    @Transactional
    public void reopenBox(UUID boxId) {
        Box box = boxes.findById(boxId)
                .orElseThrow(() -> new IllegalArgumentException("no such box: " + boxId));
        requireOpen(box.getLot());
        box.reopen();
    }

    /**
     * What should be in one carton, and how much of it has been found.
     *
     * <p>Assembled here rather than in the controller because the product behind a line is
     * fetched lazily: reading its name after the transaction has closed throws, and a caller
     * outside a transaction has no session to load it with.
     */
    @Transactional(readOnly = true)
    public List<LineToFind> linesIn(UUID boxId) {
        return expectedLines.findByBoxIdOrderByCode(boxId).stream()
                .map(
                        line ->
                                new LineToFind(
                                        line.getId(),
                                        line.getCode(),
                                        line.getProduct().getName(),
                                        line.getQuantityExpected(),
                                        line.getQuantityCounted(),
                                        line.getQuantityOutstanding()))
                .toList();
    }

    /** Cartons bearing a tracking number, for the scan that starts unpacking. */
    @Transactional(readOnly = true)
    public List<CartonFound> findByTracking(String trackingNumber) {
        return boxes.findByTrackingNumberOrderByCreatedAt(trackingNumber).stream()
                .map(
                        box ->
                                new CartonFound(
                                        box.getId(),
                                        box.getTrackingNumber(),
                                        box.getLot().getId(),
                                        box.isFinished()))
                .toList();
    }

    /** Where a lot has got to: every carton, what it holds, and whether anyone has been in it. */
    @Transactional(readOnly = true)
    public List<BoxProgress> progressOf(UUID lotId) {
        Map<UUID, List<ExpectedLine>> linesByBox =
                expectedLines.findByLotIdOrderByCode(lotId).stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        line -> line.getBox().getId()));

        return boxes.findByLotIdOrderByTrackingNumber(lotId).stream()
                .map(
                        box -> {
                            List<ExpectedLine> lines =
                                    linesByBox.getOrDefault(box.getId(), List.of());
                            long expected =
                                    lines.stream().mapToLong(ExpectedLine::getQuantityExpected).sum();
                            long counted =
                                    lines.stream().mapToLong(ExpectedLine::getQuantityCounted).sum();
                            long unlisted =
                                    unlistedFinds.findByBoxId(box.getId()).stream()
                                            .mapToLong(UnlistedFind::getQuantity)
                                            .sum();
                            return new BoxProgress(
                                    box.getId(),
                                    box.getTrackingNumber(),
                                    lines.size(),
                                    expected,
                                    counted,
                                    unlisted,
                                    box.isFinished());
                        })
                .toList();
    }

    private void requireOpen(Lot lot) {
        if (!lot.isOpen()) {
            throw new IllegalStateException(
                    "lot "
                            + lot.getId()
                            + " was closed at "
                            + lot.getClosedAt()
                            + "; its costs are settled and may already have set prices, so"
                            + " counting against it is refused");
        }
    }

    /**
     * Adds to the product's batch for this lot, creating it on first sight, and writes the
     * receipt for the increment only — not for the batch total, which would count everything
     * already found a second time.
     */
    private Batch addToBatch(
            Lot lot, Product product, long quantity, Money mrp, boolean mrpIsEstimate, Instant at) {
        Optional<Batch> existing = batches.findByLotIdAndProductId(lot.getId(), product.getId());
        Batch batch;
        if (existing.isPresent()) {
            batch = existing.get();
            batch.addCounted(quantity);
            if (mrp != null && batch.getMrp() == null) {
                batch.recordMrp(mrp, mrpIsEstimate);
            }
        } else {
            batch = batches.save(Batch.counted(product, lot, quantity, mrp, mrpIsEstimate));
        }
        ledger.save(StockLedgerEntry.receipt(product, batch, quantity, at));
        return batch;
    }

    private Product resolveOrCreate(String code, String name, String categoryCode) {
        return barcodes
                .findByCode(code)
                .map(Barcode::getProduct)
                .orElseGet(
                        () -> {
                            Product created =
                                    products.save(
                                            new Product(
                                                    name,
                                                    com.bahikhaata.contracts.Category.of(
                                                            categoryCode),
                                                    Map.of("foundUnlisted", "true")));
                            barcodes.save(new Barcode(created, code, Origin.MANUFACTURER));
                            return created;
                        });
    }

    /** One thing to look for in a carton, in the terms the screen shows it. */
    public record LineToFind(
            UUID lineId,
            String code,
            String name,
            long expected,
            long counted,
            long outstanding) {}

    /** A carton matched by the number printed on it. */
    public record CartonFound(
            UUID boxId, String trackingNumber, UUID lotId, boolean finished) {}

    /** What a count did, in the terms the screen needs: expected, found so far, difference. */
    public record CountOutcome(
            UUID batchId, long quantityExpected, long quantityCounted, long discrepancy) {}

    /** One carton's state. */
    public record BoxProgress(
            UUID boxId,
            String trackingNumber,
            int lines,
            long unitsExpected,
            long unitsCounted,
            long unitsUnlisted,
            boolean finished) {

        /** Nobody has been in it yet. */
        public boolean isNotStarted() {
            return unitsCounted == 0 && unitsUnlisted == 0 && !finished;
        }

        /** Someone has counted part of it and stopped — a normal state, not an error. */
        public boolean isInProgress() {
            return !finished && (unitsCounted > 0 || unitsUnlisted > 0);
        }
    }
}
