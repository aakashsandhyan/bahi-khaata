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
     * Closes a lot — a receiving-completeness marker. Cost was pinned per product at receipt,
     * so closing apportions nothing; it records that the boxes have been dealt with.
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

        // Cost was pinned per product at receipt, so closing apportions nothing — it only records
        // that receiving is done. The goods actually received (excluding unusable scrap and lines
        // corrected back to nothing) are summarised for the caller, and any that arrived without a
        // stated cost — a surplus — are counted so an uncosted tail is visible rather than silent.
        List<Batch> received =
                batches.findByLotId(lotId).stream()
                        .filter(batch -> batch.getCondition() != StockCondition.UNUSABLE)
                        .filter(batch -> batch.getQuantityReceived() > 0)
                        .toList();
        long uncostedSurplus = received.stream().filter(batch -> !batch.isCosted()).count();

        lot.close(at);

        return new DeliveryClosed(
                lotId,
                received.size(),
                received.stream().mapToLong(Batch::getQuantityReceived).sum(),
                lot.getAmountPaid().plus(lot.getFreight()).paise(),
                uncostedSurplus,
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
