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
import com.bahikhaata.contracts.CartonProgress;
import com.bahikhaata.contracts.CountOutcome;
import com.bahikhaata.contracts.LearntCode;
import com.bahikhaata.contracts.DeliveryProgress;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.UnpackingCarton;
import com.bahikhaata.contracts.UnpackingLine;
import com.bahikhaata.contracts.Origin;
import com.bahikhaata.contracts.RecentBox;
import com.bahikhaata.contracts.SuggestedMrp;
import com.bahikhaata.contracts.StockCondition;
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
    private final LotRepository lots;
    private final com.bahikhaata.backend.lookup.MrpBackfill mrpSuggestions;
    private final StockLedgerRepository ledger;
    private final ProductRepository products;
    private final BarcodeRepository barcodes;

    GoodsInCounting(
            ExpectedLineRepository expectedLines,
            UnlistedFindRepository unlistedFinds,
            BatchRepository batches,
            BoxRepository boxes,
            LotRepository lots,
            com.bahikhaata.backend.lookup.MrpBackfill mrpSuggestions,
            StockLedgerRepository ledger,
            ProductRepository products,
            BarcodeRepository barcodes) {
        this.expectedLines = expectedLines;
        this.unlistedFinds = unlistedFinds;
        this.batches = batches;
        this.boxes = boxes;
        this.lots = lots;
        this.mrpSuggestions = mrpSuggestions;
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
        return countExpected(
                expectedLineId, StockCondition.GOOD, quantity, mrp, mrpIsEstimate, null, at);
    }

    /**
     * As above, in a stated condition.
     *
     * <p>Damaged goods are held as their own stock rather than as a count against the sound
     * ones, because selling one has to draw from the damaged pile and leave the good pile
     * alone — and the ledger, which keys on batch, could not otherwise say which kind left.
     *
     * <p>The operator makes this call and needs no judgement for it: an item is marked or it is
     * not. What a damaged unit is then worth is decided later by someone who can see cost.
     */
    /** As below, with no remark — for sound goods and callers that record none. */
    @Transactional
    public CountOutcome countExpected(
            UUID expectedLineId,
            StockCondition condition,
            long quantity,
            Money mrp,
            boolean mrpIsEstimate,
            Instant at) {
        return countExpected(
                expectedLineId, condition, quantity, mrp, mrpIsEstimate, null, at);
    }

    @Transactional
    public CountOutcome countExpected(
            UUID expectedLineId,
            StockCondition condition,
            long quantity,
            Money mrp,
            boolean mrpIsEstimate,
            String remark,
            Instant at) {
        return countExpected(
                expectedLineId, condition, quantity, mrp, mrpIsEstimate, remark, null, at);
    }

    /**
     * As above, for goods counted straight into needs-work, naming the work they need.
     *
     * <p>The issue type is required when the condition is needs-work and must be null otherwise —
     * the same rule the batch enforces. Needs-work goods are counted against the expectation like
     * any other: they arrived, whatever state they are in.
     */
    @Transactional
    public CountOutcome countExpected(
            UUID expectedLineId,
            StockCondition condition,
            long quantity,
            Money mrp,
            boolean mrpIsEstimate,
            String remark,
            String issueType,
            Instant at) {
        ExpectedLine line =
                expectedLines
                        .findById(expectedLineId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "no such expected line: " + expectedLineId));
        requireOpen(line.getLot());

        // Counted against the expectation either way: a damaged unit still arrived, and a line
        // short by one damaged item is not short at all.
        line.recordCounted(quantity);
        line.getBox().recordActivity(at); // this box was just worked — float it up the recent rail
        Batch batch =
                addToBatch(
                        line.getLot(), line.getProduct(), condition, quantity, mrp, mrpIsEstimate,
                        remark, issueType, at);

        // Pin the manifest's stated per-unit cost onto the batch as it is received, so it is costed
        // at once and its product is priceable without the lot being closed. A line that states no
        // cost leaves the batch uncosted. Unusable scrap is never stock — it is excluded from the
        // ledger and from pricing — so it carries no inventory cost even when its line states one.
        if (line.getStatedValue() != null && condition != StockCondition.UNUSABLE) {
            batch.pinUnitCost(line.getStatedValue());
        }

        return new CountOutcome(
                batch.getId(),
                line.getQuantityExpected(),
                line.getQuantityCounted(),
                line.getDiscrepancy());
    }

    /**
     * Tags a line with the code actually printed on the goods, then counts one.
     *
     * <p>The manifest identifies goods by a marketplace identifier, which is not printed on
     * anything and which no scanner will read. The code on the pack is the maker's, or a sticker
     * the marketplace applied. They will never match, so the mapping has to be made by someone
     * holding the item — once per product, and never again.
     *
     * <p>This is the point of unpacking, not a workaround for it: it is what ties the stock on
     * the floor to the line on the sheet. Everything downstream — pricing, labelling, selling —
     * needs a code that a scanner at the counter will actually read.
     *
     * <p>A returns label already on a different product is refused — one sticker names one unit
     * and cannot be on two. A manufacturer barcode already on a different product is not: the same
     * item is routinely split across marketplace listings (variation ASINs), each imported as its
     * own row, so the code is left where it is and the unit is counted against the line in hand.
     * Collapsing the duplicate rows is deferred to pricing, where a single label is chosen.
     */
    /** As below, with no remark. */
    @Transactional
    public CountOutcome tagAndCount(
            UUID expectedLineId, String scannedCode, StockCondition condition, long quantity,
            Money mrp, boolean mrpIsEstimate, Instant at) {
        return tagAndCount(
                expectedLineId, scannedCode, condition, quantity, mrp, mrpIsEstimate, null, at);
    }

    @Transactional
    public CountOutcome tagAndCount(
            UUID expectedLineId, String scannedCode, StockCondition condition, long quantity,
            Money mrp, boolean mrpIsEstimate, String remark, Instant at) {
        return tagAndCount(
                expectedLineId, scannedCode, condition, quantity, mrp, mrpIsEstimate, remark, null,
                at);
    }

    /** As above, for goods tagged and counted straight into needs-work, naming the work needed. */
    @Transactional
    public CountOutcome tagAndCount(
            UUID expectedLineId, String scannedCode, StockCondition condition, long quantity,
            Money mrp, boolean mrpIsEstimate, String remark, String issueType, Instant at) {
        ExpectedLine line =
                expectedLines
                        .findById(expectedLineId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "no such expected line: " + expectedLineId));
        requireOpen(line.getLot());

        Product product = line.getProduct();
        barcodes.findByCode(scannedCode)
                .ifPresentOrElse(
                        existing -> reconcileCode(scannedCode, existing, product),
                        () -> barcodes.save(
                                new Barcode(product, scannedCode, originOf(scannedCode))));

        return countExpected(
                expectedLineId, condition, quantity, mrp, mrpIsEstimate, remark, issueType, at);
    }

    /**
     * Resolves a scan whose code already names a product.
     *
     * <p>Same product: nothing to do — the mapping is already there, and the count that follows is
     * all that was wanted.
     *
     * <p>A returns label ({@code LPN…}) on a <em>different</em> product is a real mistake, since one
     * sticker names one physical unit and cannot be on two; it is refused so the error is seen at
     * once. A manufacturer barcode on a different product is not a mistake: the same item is
     * routinely listed under several marketplace references (variation ASINs), each imported as its
     * own row, so the code sitting on a sibling row is expected. The code is left where it is — a
     * code stays one-to-one — and the unit is counted against the line the operator pointed at.
     * Collapsing the duplicate rows into one, and correcting the append-only ledger to match, is
     * deferred to pricing, where a single label is chosen.
     */
    private void reconcileCode(String scannedCode, Barcode existing, Product lineProduct) {
        if (existing.getProduct().getId().equals(lineProduct.getId())) {
            return;
        }
        if (originOf(scannedCode) == Origin.UNIT_LABEL) {
            throw new IllegalArgumentException(
                    "the returns label "
                            + scannedCode
                            + " is already on \""
                            + existing.getProduct().getName()
                            + "\". A returns label names one item and cannot be on two — check the"
                            + " item, or take the earlier count back.");
        }
        // A manufacturer code on a sibling variation row: count here, leave the code where it is.
    }

    /**
     * What kind of code was scanned.
     *
     * <p>A returns label names one physical unit and will never be seen again; a manufacturer's
     * barcode names the product and will appear on every future delivery of it. Both resolve to
     * the same product, but only one is worth reusing, so the difference is recorded rather than
     * flattened.
     */
    private Origin originOf(String scannedCode) {
        return scannedCode.toUpperCase(java.util.Locale.ROOT).startsWith("LPN")
                ? Origin.UNIT_LABEL
                : Origin.MANUFACTURER;
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
        box.recordActivity(at); // an extra is work on this box — float it up the recent rail

        Product product = resolveOrCreate(code, name, categoryCode);

        // A second sighting in the same carton adds to the first rather than replacing it.
        UnlistedFind find =
                unlistedFinds.findByBoxIdAndProductId(boxId, product.getId()).orElse(null);
        if (find == null) {
            find = unlistedFinds.save(new UnlistedFind(box.getLot(), box, product, quantity));
        } else {
            find.add(quantity);
        }

        Batch batch =
                addToBatch(
                        box.getLot(), product, StockCondition.GOOD, quantity, mrp, mrpIsEstimate,
                        null, null, at);

        return new CountOutcome(batch.getId(), 0, find.getQuantity(), find.getQuantity());
    }

    /**
     * Takes back a count that should not have happened.
     *
     * <p>Somebody tags a code to the wrong product, or counts an item against the wrong line,
     * and sees it a second later. Without a way back the mistake stands forever and the only
     * remedy is remembering it — which is not a remedy.
     *
     * <p>The receipt is <em>not</em> deleted. The ledger is append-only and a correction is a
     * new entry against the same batch, so what actually happened stays visible: it was counted,
     * then it was taken off. That is the truth, and an erased row would not be.
     *
     * @param untagCode a code to unmap as part of the same correction, where the count also
     *     taught the system that code. Barcodes are not history and a wrong mapping should
     *     simply cease to exist — leaving it would keep resolving the wrong goods.
     */
    @Transactional
    public void undoCount(
            UUID expectedLineId,
            StockCondition condition,
            long quantity,
            String untagCode,
            Instant at) {
        ExpectedLine line =
                expectedLines
                        .findById(expectedLineId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "no such expected line: " + expectedLineId));
        requireOpen(line.getLot());

        Batch batch = batchToCorrect(line, condition);

        StockCondition actual = batch.getCondition();
        batch.removeCounted(quantity);
        line.removeCounted(quantity);

        // Unusable goods never reached the ledger, so there is nothing there to correct.
        if (actual != StockCondition.UNUSABLE) {
            ledger.save(
                    StockLedgerEntry.adjustment(
                            line.getProduct(), batch, -quantity, at));
        }

        if (untagCode != null && !untagCode.isBlank()) {
            barcodes.findByCode(untagCode)
                    .filter(barcode -> barcode.getProduct().getId()
                            .equals(line.getProduct().getId()))
                    // Only a code this correction is responsible for. A manufacturer's barcode
                    // that arrived with the goods is not ours to remove.
                    .filter(barcode -> barcode.getOrigin() != Origin.MARKETPLACE)
                    .ifPresent(barcodes::delete);
        }
    }

    /**
     * Which batch a correction should come off.
     *
     * <p>A caller that names a condition means it. One that does not — a row on screen showing
     * only "3 counted", with no idea how those three were split between sound, damaged and
     * broken — gets the one holding the most, which is nearly always the only one.
     */
    private Batch batchToCorrect(ExpectedLine line, StockCondition condition) {
        List<Batch> held =
                batches.findByLotIdAndProductId(line.getLot().getId(), line.getProduct().getId())
                        .stream()
                        .filter(batch -> batch.getQuantityReceived() > 0)
                        .toList();
        if (held.isEmpty()) {
            throw new IllegalArgumentException(
                    "nothing is counted for that item in this delivery, so there is nothing to"
                            + " take back");
        }
        if (condition != null) {
            return held.stream()
                    .filter(batch -> batch.getCondition() == condition)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "nothing was counted as " + condition + " for that item"));
        }
        return held.stream()
                .max(java.util.Comparator.comparingLong(Batch::getQuantityReceived))
                .orElseThrow();
    }

    /**
     * Every code that scans as a line's goods.
     *
     * <p>Asked when someone is correcting a mistake, because taking a count back does not undo
     * the mapping that scan created — and the mapping is the half that keeps causing trouble
     * afterwards.
     */
    @Transactional(readOnly = true)
    public List<LearntCode> codesFor(UUID expectedLineId) {
        ExpectedLine line =
                expectedLines
                        .findById(expectedLineId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "no such expected line: " + expectedLineId));
        return barcodes.findByProductId(line.getProduct().getId()).stream()
                .map(
                        barcode ->
                                new LearntCode(
                                        barcode.getCode(),
                                        barcode.getOrigin(),
                                        barcode.getOrigin() != Origin.MARKETPLACE))
                .toList();
    }

    /**
     * Forgets a code, so it can be scanned onto something else.
     *
     * <p>The dead end this exists for: a sticker was tagged to the wrong goods, the count was
     * taken back, and the sticker still pointed at those goods — so scanning it again landed on
     * a line with nothing left to count and there was nowhere to go.
     *
     * <p>Only codes learnt here. A supplier's own reference came with the manifest and is how a
     * line is matched back to it; removing that would break the delivery rather than fix a
     * mistake.
     *
     * <p>Barcodes are not history. A mapping that points at the wrong thing should cease to
     * exist rather than be marked wrong, because anything left behind will go on resolving
     * confidently.
     */
    @Transactional
    public void releaseCode(String code) {
        Barcode barcode =
                barcodes.findByCode(code)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "nothing is scanning as " + code));
        if (barcode.getOrigin() == Origin.MARKETPLACE) {
            throw new IllegalArgumentException(
                    code
                            + " is the supplier's own reference for these goods, not a code from"
                            + " the item. It is how the line is matched to the manifest and"
                            + " cannot be given away.");
        }
        barcodes.delete(barcode);
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
        box.recordActivity(at);
    }

    /** Reopens a carton marked finished by mistake. */
    @Transactional
    public void reopenBox(UUID boxId, Instant at) {
        Box box = boxes.findById(boxId)
                .orElseThrow(() -> new IllegalArgumentException("no such box: " + boxId));
        requireOpen(box.getLot());
        box.reopen();
        box.recordActivity(at); // reopened to be worked again — bring it back to the top
    }

    /**
     * What should be in one carton, and how much of it has been found.
     *
     * <p>Assembled here rather than in the controller because the product behind a line is
     * fetched lazily: reading its name after the transaction has closed throws, and a caller
     * outside a transaction has no session to load it with.
     */
    @Transactional(readOnly = true)
    public List<UnpackingLine> linesIn(UUID boxId) {
        List<ExpectedLine> inBox = expectedLines.findByBoxIdOrderByCode(boxId);
        java.math.BigDecimal rate =
                inBox.isEmpty()
                        ? null
                        : ratePaidFor(inBox.get(0).getLot());
        return inBox.stream()
                .map(
                        line ->
                                new UnpackingLine(
                                        line.getId(),
                                        line.getCode(),
                                        line.getProduct().getName(),
                                        line.getProduct().getCategory().code(),
                                        line.getQuantityExpected(),
                                        line.getQuantityCounted(),
                                        line.getQuantityOutstanding(),
                                        needsMrp(line),
                                        line.getStatedValue() == null
                                                ? null
                                                : line.getStatedValue().paise(),
                                        line.getProduct().getOnlinePrice() == null
                                                ? null
                                                : line.getProduct().getOnlinePrice().paise(),
                                        indicativeCost(line, rate),
                                        recordedMrp(line)))
                .toList();
    }

    /**
     * Whether the printed price still has to be read off these goods.
     *
     * <p>Per product per delivery rather than per unit: the figure is printed on the pack and
     * every pack in one delivery carries the same one. Asking again for each unit would be
     * friction with nothing behind it, and friction is what makes people stop recording things.
     */
    private boolean needsMrp(ExpectedLine line) {
        // Asked once per product per delivery whatever condition the units turn out to be in:
        // the MRP is printed on the pack, and a dented box carries the same printed price as a
        // clean one.
        return batches.findByLotIdAndProductId(line.getLot().getId(), line.getProduct().getId())
                .stream()
                .noneMatch(batch -> batch.getMrp() != null);
    }

    /**
     * The MRP already read off this line's goods this delivery, or null if none has been yet.
     *
     * <p>The figure {@link #needsMrp} decides by its presence, returned so the screen can show it
     * back on a later unit of the same product rather than ask for a price it already holds.
     */
    private Long recordedMrp(ExpectedLine line) {
        return batches.findByLotIdAndProductId(line.getLot().getId(), line.getProduct().getId())
                .stream()
                .map(Batch::getMrp)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(Money::paise)
                .orElse(null);
    }

    /**
     * What fraction of its stated value a delivery was bought at.
     *
     * <p>The amount paid over the total the sheet says the goods are worth: a quarter of the
     * online price on one delivery, ten per cent above the supplier's own cost on another. It
     * is the same figure the apportionment will arrive at, computed here from what was expected
     * rather than from what turned up.
     *
     * <p>Null when the sheet states no values at all, in which case nothing can be indicated.
     */
    private java.math.BigDecimal ratePaidFor(Lot lot) {
        long stated = 0;
        for (ExpectedLine line : expectedLines.findByLotIdOrderByCode(lot.getId())) {
            if (line.getStatedValue() != null) {
                stated += line.getStatedValue().paise() * line.getQuantityExpected();
            }
        }
        if (stated == 0) {
            return null;
        }
        return java.math.BigDecimal.valueOf(lot.getAmountPaid().plus(lot.getFreight()).paise())
                .divide(java.math.BigDecimal.valueOf(stated), 8, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Roughly what a unit of this line will have cost.
     *
     * <p>An indication, not the cost. The real figure is settled when the delivery closes and is
     * spread across the goods that actually arrived — so a line that turns up short carries the
     * same money over fewer units and costs more each than this says. Shown anyway because a
     * rough figure while the goods are in hand beats an exact one nobody will look up later.
     */
    private Long indicativeCost(ExpectedLine line, java.math.BigDecimal rate) {
        if (rate == null || line.getStatedValue() == null) {
            return null;
        }
        return rate.multiply(java.math.BigDecimal.valueOf(line.getStatedValue().paise()))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValue();
    }

    /**
     * Which line in a carton a scanned code belongs to, if any is already known.
     *
     * <p>Asked on every scan, because a product accumulates codes: the supplier's marketplace
     * reference, the maker's printed barcode, and a returns sticker for each unit. Comparing a
     * scan against the manifest's reference alone would send someone back to "which item is
     * this" for a code the system already knows perfectly well.
     */
    @Transactional(readOnly = true)
    public Optional<UnpackingLine> resolveInBox(UUID boxId, String scannedCode) {
        return barcodes
                .findByCode(scannedCode)
                .map(Barcode::getProduct)
                .flatMap(product -> expectedLines.findByBoxIdAndProductId(boxId, product.getId()))
                .flatMap(
                        line ->
                                linesIn(boxId).stream()
                                        .filter(view -> view.lineId().equals(line.getId()))
                                        .findFirst());
    }

    /** What a lookup thinks this line's goods cost at retail. Offered, never applied. */
    @Transactional(readOnly = true)
    public SuggestedMrp suggestMrpFor(UUID expectedLineId) {
        ExpectedLine line =
                expectedLines
                        .findById(expectedLineId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "no such expected line: " + expectedLineId));
        return mrpSuggestions.suggestFor(line.getProduct().getId());
    }

    /** Cartons bearing a tracking number, for the scan that starts unpacking. */
    @Transactional(readOnly = true)
    public List<UnpackingCarton> findByTracking(String trackingNumber) {
        return boxes.findByTrackingNumberOrderByCreatedAt(trackingNumber).stream()
                .map(
                        box ->
                                new UnpackingCarton(
                                        box.getId(),
                                        box.getTrackingNumber(),
                                        box.getLot().getId(),
                                        box.isFinished()))
                .toList();
    }

    /** Where a lot has got to: every carton, what it holds, and whether anyone has been in it. */
    @Transactional(readOnly = true)
    public List<CartonProgress> progressOf(UUID lotId) {
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
                            return new CartonProgress(
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

    /**
     * The boxes worked most recently, newest first — the rail that offers back the ones in hand.
     *
     * <p>Recency is the last count, extra, finish, or reopen, not the manifest order: a box nobody
     * has touched does not appear. Only the few asked for are fetched, and each carries just enough
     * to read at a glance and to reopen on a tap.
     */
    @Transactional(readOnly = true)
    public List<RecentBox> recentBoxes(int limit) {
        return boxes
                .findByLastActivityAtIsNotNullOrderByLastActivityAtDesc(
                        org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(
                        box -> {
                            List<ExpectedLine> lines =
                                    expectedLines.findByBoxIdOrderByCode(box.getId());
                            long expected =
                                    lines.stream().mapToLong(ExpectedLine::getQuantityExpected).sum();
                            long counted =
                                    lines.stream().mapToLong(ExpectedLine::getQuantityCounted).sum();
                            long unlisted =
                                    unlistedFinds.findByBoxId(box.getId()).stream()
                                            .mapToLong(UnlistedFind::getQuantity)
                                            .sum();
                            String category =
                                    lines.isEmpty()
                                            ? ""
                                            : lines.get(0).getProduct().getCategory().code();
                            return new RecentBox(
                                    box.getId(),
                                    box.getTrackingNumber(),
                                    category,
                                    expected,
                                    counted,
                                    unlisted,
                                    box.isFinished(),
                                    box.getLastActivityAt());
                        })
                .toList();
    }

    /**
     * How far a delivery has been unpacked.
     *
     * <p>Includes the count of items still waiting on a printed price, because that is the queue
     * that decides when anything can actually be sold — cartons being empty is not the finish
     * line, an MRP against every item is.
     */
    @Transactional(readOnly = true)
    public DeliveryProgress progressOfDelivery(UUID lotId) {
        Lot lot = lots.findById(lotId)
                .orElseThrow(() -> new IllegalArgumentException("no such delivery: " + lotId));

        List<CartonProgress> cartons = progressOf(lotId);
        List<ExpectedLine> lines = expectedLines.findByLotIdOrderByCode(lotId);

        long withoutMrp =
                batches.findByLotId(lotId).stream().filter(batch -> batch.getMrp() == null).count();

        return new DeliveryProgress(
                lotId,
                lot.getSupplier(),
                lines.isEmpty() ? "" : lines.get(0).getProduct().getCategory().code(),
                cartons.size(),
                (int) cartons.stream().filter(CartonProgress::finished).count(),
                (int) cartons.stream().filter(CartonProgress::inProgress).count(),
                lines.stream().mapToLong(ExpectedLine::getQuantityExpected).sum(),
                lines.stream().mapToLong(ExpectedLine::getQuantityCounted).sum(),
                unlistedFinds.findByLotId(lotId).stream().mapToLong(UnlistedFind::getQuantity).sum(),
                withoutMrp,
                !lot.isOpen());
    }

    /**
     * Every delivery and how far each has been unpacked.
     *
     * <p>Ordered largest first, since that is the order they will be worked through and the one
     * that makes the remaining effort legible at a glance.
     */
    @Transactional(readOnly = true)
    public List<DeliveryProgress> allDeliveries() {
        return lots.findAll().stream()
                .map(lot -> progressOfDelivery(lot.getId()))
                .sorted((a, b) -> Long.compare(b.unitsExpected(), a.unitsExpected()))
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
    /**
     * Materialises stock keyed in by hand at pricing into a batch and a stock-ledger receipt.
     *
     * <p>Pricing reaches for this when an item cannot be scanned to an existing record — its
     * LSN/LPN reference is lost, or it was never counted — so it is entering the system here for
     * the first time. It runs the same {@link #addToBatch} path counting uses, so the rule that
     * only GOOD/DAMAGED units reach the ledger, and the MRP handling, live in one place rather
     * than being duplicated in the pricing module.
     */
    @Transactional
    public Batch receiveManual(
            Lot lot,
            Product product,
            StockCondition condition,
            long quantity,
            Money mrp,
            boolean mrpIsEstimate,
            Instant at) {
        requireOpen(lot);
        return addToBatch(lot, product, condition, quantity, mrp, mrpIsEstimate, null, null, at);
    }

    // Package-private: the remediation service adds to a target batch through this same path, so
    // the MRP inheritance and the off-ledger rule below are written once.
    Batch addToBatch(
            Lot lot,
            Product product,
            StockCondition condition,
            long quantity,
            Money mrp,
            boolean mrpIsEstimate,
            String remark,
            String issueType,
            Instant at) {
        // The printed price is a property of the product in this delivery, not of one condition:
        // a dented pack carries the same MRP as a clean one. `needsMrp` promises it is asked once
        // per product and reused across conditions, but the answer is only ever stored on the
        // batch that was asked. So when a count arrives with none — the sibling condition that
        // slipped past the prompt — take the product's MRP off whichever batch already carries it,
        // rather than leaving these goods silently unpriced.
        if (mrp == null) {
            Optional<Batch> priced =
                    batches.findByLotIdAndProductId(lot.getId(), product.getId()).stream()
                            .filter(b -> b.getMrp() != null)
                            .findFirst();
            if (priced.isPresent()) {
                mrp = priced.get().getMrp();
                mrpIsEstimate = priced.get().isMrpEstimate();
            }
        }

        // A needs-work batch is keyed also by the kind of work, since a product may hold units
        // needing different work at once; the other conditions have one batch per condition.
        Optional<Batch> existing =
                condition == StockCondition.NEEDS_WORK
                        ? batches.findByLotIdAndProductIdAndConditionAndIssueType(
                                lot.getId(), product.getId(), condition, issueType)
                        : batches.findByLotIdAndProductIdAndCondition(
                                lot.getId(), product.getId(), condition);
        Batch batch;
        if (existing.isPresent()) {
            batch = existing.get();
            batch.addCounted(quantity);
            if (mrp != null && batch.getMrp() == null) {
                batch.recordMrp(mrp, mrpIsEstimate);
            }
        } else {
            batch = batches.save(
                    Batch.counted(product, lot, condition, quantity, mrp, mrpIsEstimate, issueType));
        }
        batch.addRemark(remark);
        // GOOD and DAMAGED are stock the moment they are counted; UNUSABLE never was, and
        // NEEDS_WORK is not until it is prepared and moved to a sellable state. Only the two
        // stock-bearing conditions reach the ledger — writing then reversing a receipt for the
        // others would add entries that cancel out and a moment where on-hand is wrong.
        if (condition == StockCondition.GOOD || condition == StockCondition.DAMAGED) {
            ledger.save(StockLedgerEntry.receipt(product, batch, quantity, at));
        }
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

}
