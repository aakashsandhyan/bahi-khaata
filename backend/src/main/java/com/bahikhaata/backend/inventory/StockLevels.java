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

import com.bahikhaata.contracts.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How much stock there is, derived from the ledger.
 *
 * <p>Nothing stores a quantity-remaining counter. The ledger is the source of truth, and a
 * stored total could silently drift out of step with the movements that produced it — leaving
 * two numbers and no way to tell which is right. Deriving costs a SUM; it buys the guarantee
 * that on-hand always agrees with history.
 */
@Service
public class StockLevels {

    private final StockLedgerRepository ledger;
    private final BatchRepository batches;

    StockLevels(StockLedgerRepository ledger, BatchRepository batches) {
        this.ledger = ledger;
        this.batches = batches;
    }

    /** Units of a product available to sell, across all its batches. */
    @Transactional(readOnly = true)
    public long onHand(UUID productId) {
        return ledger.quantityOnHand(productId);
    }

    /** Units still drawable from one batch — what FIFO consumes in order. */
    @Transactional(readOnly = true)
    public long onHandForBatch(UUID batchId) {
        return ledger.quantityOnHandForBatch(batchId);
    }

    /**
     * What this batch has actually cost in sales so far — the cost side of its margin.
     *
     * <p>This is the figure FIFO exists to make answerable. Averaging costs across lots gives
     * the same total across a whole sale, but attributes none of it to a particular delivery,
     * so "was that supplier's pallet worth buying again?" becomes unanswerable.
     *
     * <p>Summed in Java over the batch's movements rather than in SQL, because the amount is
     * a converted value type and the arithmetic stays exact integer paise throughout.
     */
    @Transactional(readOnly = true)
    public Money costOfGoodsSoldForBatch(UUID batchId) {
        return ledger.findByBatchId(batchId).stream()
                .map(StockLedgerEntry::getCogs)
                .filter(Objects::nonNull)
                .reduce(Money.ZERO, Money::plus);
    }

    /**
     * Quantity on hand as it stood at a moment in the past — movements effective after that
     * moment are excluded.
     *
     * <p>Reconstructible rather than remembered: because the ledger keeps every movement with
     * the time it was effective, any past position can be recomputed, including one that a
     * backdated entry has since changed.
     */
    @Transactional(readOnly = true)
    public long onHandAsAt(UUID productId, Instant asAt) {
        return ledger.quantityOnHandAsAt(productId, asAt);
    }

    /**
     * What the stock of a product was worth at a moment in the past, at the cost of the
     * batches it actually consisted of.
     *
     * <p>Valued per batch rather than at an average, for the same reason cost of goods sold is
     * attributed per batch: an average hides which delivery the value came from.
     */
    @Transactional(readOnly = true)
    public Money valuationAsAt(UUID productId, Instant asAt) {
        return valuationDetailAsAt(productId, asAt).valued();
    }

    /**
     * A product's value, with stock whose cost is not yet settled reported separately.
     *
     * <p>Goods counted out of a carton hold no cost until their lot is closed, and there is no
     * honest single number covering both. Adding them in at zero would understate the stock and
     * make every margin computed from it read as pure profit; leaving them out silently would
     * make the total look complete when it is not. So they are counted and reported apart, and
     * the caller has to decide what to do about them.
     */
    @Transactional(readOnly = true)
    public Valuation valuationDetailAsAt(UUID productId, Instant asAt) {
        Money valued = Money.ZERO;
        long uncostedUnits = 0;
        for (Batch batch : batches.findByProductIdInFifoOrder(productId)) {
            long remaining = ledger.quantityOnHandForBatchAsAt(batch.getId(), asAt);
            if (remaining <= 0) {
                continue;
            }
            if (batch.isCosted()) {
                valued = valued.plus(batch.getAllocatedUnitCost().times(remaining));
            } else {
                uncostedUnits += remaining;
            }
        }
        return new Valuation(valued, uncostedUnits);
    }

    /**
     * What stock is worth, and how much of it cannot yet be said.
     *
     * @param valued the value of stock whose cost is settled
     * @param uncostedUnits units held from lots that are still open — excluded from the value
     *     above rather than counted at zero
     */
    public record Valuation(Money valued, long uncostedUnits) {

        /** Whether the figure covers everything on hand, or only part of it. */
        public boolean isComplete() {
            return uncostedUnits == 0;
        }
    }

    /**
     * What remains of each of a product's batches, oldest delivery first — the order stock is
     * consumed in.
     *
     * <p>Exhausted batches are left out: they are history, and a caller asking what is on the
     * shelf does not want a list padded with zeroes.
     */
    @Transactional(readOnly = true)
    public List<BatchStock> remainingByBatch(UUID productId) {
        List<BatchStock> remaining = new ArrayList<>();
        for (Batch batch : batches.findByProductIdInFifoOrder(productId)) {
            long left = ledger.quantityOnHandForBatch(batch.getId());
            if (left > 0) {
                remaining.add(new BatchStock(batch, left));
            }
        }
        return remaining;
    }
}
