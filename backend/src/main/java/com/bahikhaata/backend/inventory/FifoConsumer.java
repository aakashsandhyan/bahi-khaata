/*
 * bahi-khaata — point of sale for Bachat Bazaar
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
     * <p>A sale is never refused for want of counted stock: at the counter the physical item is
     * the truth, and a rough count must not stop it being sold. When the count comes up short, the
     * shortfall is attributed to the <em>newest</em> batch, which is driven negative — a true,
     * timestamped record that more was sold than the count showed, and the signal to recount. A
     * priced product always has at least one batch to attribute to.
     */
    @Transactional(readOnly = true)
    public List<BatchDraw> plan(UUID productId, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity to consume must be positive");
        }

        List<Batch> fifo = batches.findByProductIdInFifoOrder(productId);
        List<BatchDraw> draws = new ArrayList<>();
        long outstanding = quantity;

        for (Batch batch : fifo) {
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

        if (outstanding > 0 && !fifo.isEmpty()) {
            // Oversold against the count. Put the shortfall on the newest batch (last in FIFO
            // order), merging into its draw if it already has one, so it goes negative.
            Batch newest = fifo.get(fifo.size() - 1);
            int existing = -1;
            for (int i = 0; i < draws.size(); i++) {
                if (draws.get(i).batch().getId().equals(newest.getId())) {
                    existing = i;
                    break;
                }
            }
            if (existing >= 0) {
                draws.set(existing, new BatchDraw(newest, draws.get(existing).quantity() + outstanding));
            } else {
                draws.add(new BatchDraw(newest, outstanding));
            }
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
            // Cost of goods sold at the batch's own cost. An uncosted batch (priced before its lot
            // was costed) has no known cost, so it contributes zero rather than blocking the sale —
            // margin on those units simply reads as unknown, never on the customer bill.
            Money cogs = batch.isCosted()
                    ? batch.getAllocatedUnitCost().times(draw.quantity())
                    : Money.ZERO;
            movements.add(
                    ledger.save(
                            StockLedgerEntry.sale(
                                    batch.getProduct(), batch, draw.quantity(), cogs, effectiveAt)));
        }
        return movements;
    }
}
