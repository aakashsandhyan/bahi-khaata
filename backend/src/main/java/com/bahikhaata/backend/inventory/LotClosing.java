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

import com.bahikhaata.backend.inventory.allocation.AllocatedLine;
import com.bahikhaata.backend.inventory.allocation.Allocation;
import com.bahikhaata.backend.inventory.allocation.AllocationLine;
import com.bahikhaata.backend.inventory.allocation.CostAllocator;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.DeliveryClosed;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settling what a delivery cost, once it has all been counted.
 *
 * <p>Apportionment happens here and nowhere else. A lot's shares depend on every line in it, so
 * none can be final while a carton is still unopened — which is why a batch carries stock at an
 * unknown cost until this runs.
 *
 * <p>The amount is spread across <em>what actually arrived</em>, not what was promised. The
 * money was paid regardless of what turned up, so the goods that did turn up must carry all of
 * it: eleven units where twelve were expected carry the whole line's cost between them and each
 * cost a little more. That is not an error to correct — it is what the shortfall cost.
 *
 * <p>A caution that cost real money once: shares summing exactly to the amount paid proves
 * nothing about whether the split is right. Shares of an amount sum to that amount however
 * wrongly they are divided. The tests here therefore compare lines against one another, which
 * is the only place a misdistribution shows.
 */
@Service
public class LotClosing {

    private final LotRepository lots;
    private final BatchRepository batches;
    private final ExpectedLineRepository expectedLines;
    private final UnlistedFindRepository unlistedFinds;
    private final BoxRepository boxes;
    private final CostAllocator allocator;
    private final ReceivingService receivingService;

    LotClosing(
            LotRepository lots,
            BatchRepository batches,
            ExpectedLineRepository expectedLines,
            UnlistedFindRepository unlistedFinds,
            BoxRepository boxes,
            CostAllocator allocator,
            ReceivingService receivingService) {
        this.lots = lots;
        this.batches = batches;
        this.expectedLines = expectedLines;
        this.unlistedFinds = unlistedFinds;
        this.boxes = boxes;
        this.allocator = allocator;
        this.receivingService = receivingService;
    }

    /**
     * Cartons nobody has opened. Reported before closing so the decision to write them off is
     * taken deliberately rather than discovered afterwards.
     */
    @Transactional(readOnly = true)
    public List<String> unopenedCartons(UUID lotId) {
        Map<UUID, Long> countedByBox = new HashMap<>();
        for (ExpectedLine line : expectedLines.findByLotIdOrderByCode(lotId)) {
            countedByBox.merge(line.getBox().getId(), line.getQuantityCounted(), Long::sum);
        }
        for (UnlistedFind find : unlistedFinds.findByLotId(lotId)) {
            countedByBox.merge(find.getBox().getId(), find.getQuantity(), Long::sum);
        }
        return boxes.findByLotIdOrderByTrackingNumber(lotId).stream()
                .filter(box -> !box.isFinished() && countedByBox.getOrDefault(box.getId(), 0L) == 0)
                .map(Box::getTrackingNumber)
                .toList();
    }

    /**
     * Closes a lot and apportions what was paid across the goods that actually arrived.
     *
     * <p>Cartons left unopened do not prevent this. Goods that never came would otherwise hold
     * a lot open forever, and nothing in it could be priced or sold — so closing over them is
     * allowed, but only deliberately: the cartons are reported and the caller must confirm.
     */
    @Transactional
    public DeliveryClosed close(UUID lotId, boolean confirmUnopenedCartons, Instant at) {
        Lot lot = lots.findById(lotId)
                .orElseThrow(() -> new IllegalArgumentException("no such lot: " + lotId));
        if (!lot.isOpen()) {
            throw new IllegalStateException(
                    "lot " + lotId + " was already closed at " + lot.getClosedAt());
        }

        // Verify all boxes are in terminal state (UNPACKED, NOT_RECEIVED, or REJECTED)
        receivingService.validateLotCanClose(lotId);

        List<String> unopened = unopenedCartons(lotId);
        if (!unopened.isEmpty() && !confirmUnopenedCartons) {
            throw new UnopenedCartonsException(unopened);
        }

        List<Batch> uncosted = batches.findByLotId(lotId).stream()
                .filter(batch -> !batch.isCosted())
                .toList();

        // Goods fit for nothing take no share. What they would have carried stays with the ones
        // that can be sold, which is what the delivery really cost to get sellable stock out of.
        List<Batch> unusable =
                uncosted.stream().filter(batch -> batch.getCondition() == StockCondition.UNUSABLE)
                        .toList();
        List<Batch> received =
                uncosted.stream()
                        .filter(batch -> batch.getCondition() != StockCondition.UNUSABLE)
                        // A batch corrected back to nothing has nothing to carry a share, and
                        // asking the allocator to divide by zero would fail rather than say so.
                        .filter(batch -> batch.getQuantityReceived() > 0)
                        .toList();
        if (received.isEmpty()) {
            throw new IllegalStateException(
                    "lot "
                            + lotId
                            + (unusable.isEmpty()
                                    ? " has nothing counted against it, so there is nothing to"
                                            + " carry what was paid. Count at least one carton"
                                            + " first."
                                    : " holds nothing but unusable goods, so there is nothing"
                                            + " that can carry what was paid. Record the loss"
                                            + " against the delivery instead of closing it."));
        }

        Map<UUID, Money> statedByProduct = statedValuePerUnitByProduct(lotId);
        Money lotAverage = averageUnitValue(statedByProduct, received);
        if (lotAverage == null) {
            throw new IllegalArgumentException(
                    "lot " + lotId + ": no line states a value, so there is nothing to apportion"
                            + " by and no average to fall back on. Supply a value for at least"
                            + " one line.");
        }

        List<AllocationLine> lines = new ArrayList<>();
        List<Boolean> estimated = new ArrayList<>();
        for (Batch batch : received) {
            Money stated = statedByProduct.get(batch.getProduct().getId());
            boolean isEstimate = stated == null;
            lines.add(
                    new AllocationLine(
                            batch.getId().toString(),
                            batch.getQuantityReceived(),
                            batch.getQuantityDamaged(),
                            isEstimate ? lotAverage : stated,
                            null));
            estimated.add(isEstimate);
        }

        Allocation allocation = allocator.allocate(lot.getAmountPaid(), lot.getFreight(), lines);

        long estimatedCount = 0;
        for (int i = 0; i < received.size(); i++) {
            AllocatedLine allocated = allocation.lines().get(i);
            boolean isEstimate = estimated.get(i);
            if (isEstimate) {
                estimatedCount++;
            }
            received.get(i)
                    .applyAllocation(
                            allocated.allocatedTotal(),
                            allocated.allocatedUnitCost(),
                            // Recorded as estimated where the weight was the lot average rather
                            // than a figure the manifest stated, so a margin resting on it can
                            // be recognised for what it is.
                            isEstimate ? CostBasis.ESTIMATED : allocated.basis());
        }

        // Recorded as absorbed rather than left at a bare zero, which reads like an oversight.
        for (Batch scrap : unusable) {
            scrap.applyAllocation(Money.ZERO, Money.ZERO, CostBasis.ABSORBED);
        }

        lot.close(at);

        return new DeliveryClosed(
                lotId,
                received.size(),
                received.stream().mapToLong(Batch::getQuantityReceived).sum(),
                lot.getAmountPaid().plus(lot.getFreight()).paise(),
                estimatedCount,
                unopened);
    }

    /**
     * Each product's per-unit stated value, averaged across the cartons it was expected in.
     *
     * <p>Weighted by expected quantity, because the stated value is a claim about the goods and
     * a claim covering nine units should count for more than one covering a single unit.
     * Products whose lines all state nothing are absent, and fall to the lot average.
     */
    private Map<UUID, Money> statedValuePerUnitByProduct(UUID lotId) {
        Map<UUID, long[]> totals = new HashMap<>();
        for (ExpectedLine line : expectedLines.findByLotIdOrderByCode(lotId)) {
            if (line.getStatedValue() == null) {
                continue;
            }
            long[] sums = totals.computeIfAbsent(line.getProduct().getId(), id -> new long[2]);
            sums[0] += line.getStatedValue().paise() * line.getQuantityExpected();
            sums[1] += line.getQuantityExpected();
        }
        Map<UUID, Money> perUnit = new HashMap<>();
        totals.forEach(
                (productId, sums) -> {
                    if (sums[1] > 0) {
                        perUnit.put(productId, Money.ofPaise(sums[0] / sums[1]));
                    }
                });
        return perUnit;
    }

    /**
     * The lot's average unit value, used for goods with no stated value of their own.
     *
     * <p>Computed over the quantities actually counted rather than expected, so the average
     * reflects the mix that really turned up — the same quantities the apportionment itself
     * runs over.
     *
     * <p>A genuine estimate: right on average, wrong on any particular item. A surplus carton
     * of something dear comes out undercosted and something cheap overcosted. Accepted because
     * the alternatives are worse — leaving the goods uncosted strands them off the shelf, and
     * excluding them makes every margin computed from them read as pure profit.
     *
     * <p>Null when no line states a value at all, which the caller refuses on.
     */
    private Money averageUnitValue(Map<UUID, Money> statedByProduct, List<Batch> received) {
        long value = 0;
        long quantity = 0;
        for (Batch batch : received) {
            Money stated = statedByProduct.get(batch.getProduct().getId());
            if (stated != null) {
                value += stated.paise() * batch.getQuantityReceived();
                quantity += batch.getQuantityReceived();
            }
        }
        return quantity == 0 ? null : Money.ofPaise(Math.max(1, value / quantity));
    }

    /** Raised rather than returned: closing over unopened cartons must be a decision. */
    public static class UnopenedCartonsException extends RuntimeException {
        private final List<String> trackingNumbers;

        UnopenedCartonsException(List<String> trackingNumbers) {
            super(
                    trackingNumbers.size()
                            + " cartons have not been opened ("
                            + String.join(", ", trackingNumbers.subList(
                                    0, Math.min(5, trackingNumbers.size())))
                            + (trackingNumbers.size() > 5 ? ", …" : "")
                            + "). Closing now gives their goods no share of what was paid."
                            + " Confirm to proceed.");
            this.trackingNumbers = List.copyOf(trackingNumbers);
        }

        public List<String> getTrackingNumbers() {
            return trackingNumbers;
        }
    }

}
