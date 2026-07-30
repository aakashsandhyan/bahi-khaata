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

import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.DeliveryClosed;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closing a delivery once it has all been counted — a receiving-completeness marker.
 *
 * <p>Cost is not settled here. Each product's cost was pinned from its manifest line as it was
 * received (see {@code GoodsInCounting} and the stock-ledger spec), so a product is costed and
 * priceable the moment it arrives, not when its lot closes. Closing records that the boxes have
 * been dealt with, and reports any left unopened so writing them off is a deliberate decision
 * rather than something discovered later.
 */
@Service
public class LotClosing {

    private final LotRepository lots;
    private final BatchRepository batches;
    private final ExpectedLineRepository expectedLines;
    private final UnlistedFindRepository unlistedFinds;
    private final BoxRepository boxes;
    private final ReceivingService receivingService;

    LotClosing(
            LotRepository lots,
            BatchRepository batches,
            ExpectedLineRepository expectedLines,
            UnlistedFindRepository unlistedFinds,
            BoxRepository boxes,
            ReceivingService receivingService) {
        this.lots = lots;
        this.batches = batches;
        this.expectedLines = expectedLines;
        this.unlistedFinds = unlistedFinds;
        this.boxes = boxes;
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
