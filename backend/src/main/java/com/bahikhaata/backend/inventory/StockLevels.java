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

    StockLevels(StockLedgerRepository ledger) {
        this.ledger = ledger;
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
}
