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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes stock oldest-batch-first.
 *
 * <p>FIFO was chosen over weighted average for per-lot margin visibility: knowing what a
 * given liquidation supplier's pallet actually earned is a recurring decision in this
 * business, and averaging costs across lots destroys exactly that.
 *
 * <p>Oldest is by the lot's <em>business delivery date</em>, not when the row was written, so
 * a delivery logged two days late still consumes in true arrival order.
 */
@Service
public class FifoConsumer {

    private final BatchRepository batches;
    private final StockLedgerRepository ledger;

    FifoConsumer(BatchRepository batches, StockLedgerRepository ledger) {
        this.batches = batches;
        this.ledger = ledger;
    }

    /**
     * Works out which batches a consumption would draw from, and how much from each, without
     * writing anything.
     *
     * @throws InsufficientStockException if the product does not have enough on hand, naming
     *     what is available — the cashier has to be told which product is short
     */
    @Transactional(readOnly = true)
    public List<BatchDraw> plan(UUID productId, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity to consume must be positive");
        }

        List<BatchDraw> draws = new ArrayList<>();
        long outstanding = quantity;

        for (Batch batch : batches.findByProductIdInFifoOrder(productId)) {
            if (outstanding == 0) {
                break;
            }
            long remaining = ledger.quantityOnHandForBatch(batch.getId());
            if (remaining <= 0) {
                // Exhausted, or never received. Move to the next oldest.
                continue;
            }
            long taken = Math.min(remaining, outstanding);
            draws.add(new BatchDraw(batch, taken));
            outstanding -= taken;
        }

        if (outstanding > 0) {
            throw new InsufficientStockException(productId, quantity, quantity - outstanding);
        }
        return draws;
    }

    /**
     * Consumes stock for a sale, appending one movement per batch drawn from.
     *
     * <p>Cost of goods sold is attributed at each batch's own cost, so a sale spanning two
     * batches bought at different prices records each portion at what it actually cost. That
     * is the point of FIFO here: a single averaged figure would hide which lot earned what.
     */
    @Transactional
    public List<StockLedgerEntry> consumeForSale(
            UUID productId, long quantity, Instant effectiveAt) {
        List<StockLedgerEntry> movements = new ArrayList<>();

        for (BatchDraw draw : plan(productId, quantity)) {
            Batch batch = draw.batch();
            movements.add(
                    ledger.save(
                            StockLedgerEntry.sale(
                                    batch.getProduct(),
                                    batch,
                                    draw.quantity(),
                                    batch.getAllocatedUnitCost().times(draw.quantity()),
                                    effectiveAt)));
        }
        return movements;
    }
}
