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
}
