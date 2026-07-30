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

import com.bahikhaata.contracts.LotPhantomReport;
import com.bahikhaata.contracts.PhantomLine;
import com.bahikhaata.contracts.WriteOffResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciling a lot's phantom stock.
 *
 * <p>When a counted item's reference is lost, it is re-entered by hand as new stock, leaving its
 * original counted batch orphaned — counted, never priced, with no physical match. The double-count
 * cannot be resolved per unit (the whole problem is that the unit could not be identified), so it
 * is netted at the lot: the on-hand stock of every batch in the lot whose product was never priced
 * is written off as shrinkage. The write-off is an append-only negative ledger movement per batch;
 * no existing row is edited.
 *
 * <p>A batch whose product simply has not been priced <em>yet</em> would also count as phantom, so
 * reconciliation is a deliberate step taken when a lot's pricing is complete — by then, anything
 * still unpriced is genuinely a phantom.
 */
@Service
public class LotReconciliation {

    private final BatchRepository batches;
    private final StockLevels stockLevels;
    private final StockLedgerRepository ledger;

    LotReconciliation(BatchRepository batches, StockLevels stockLevels, StockLedgerRepository ledger) {
        this.batches = batches;
        this.stockLevels = stockLevels;
        this.ledger = ledger;
    }

    /** The lot's phantom stock: on-hand units in batches whose product was never priced. */
    @Transactional(readOnly = true)
    public LotPhantomReport phantomReport(UUID lotId) {
        List<PhantomLine> lines = batches.findByLotId(lotId).stream()
                .filter(batch -> !batch.getProduct().isPriced())
                .map(batch -> new PhantomLine(
                        batch.getProduct().getId(),
                        batch.getProduct().getName(),
                        batch.getId(),
                        stockLevels.onHandForBatch(batch.getId())))
                .filter(line -> line.quantity() > 0)
                .toList();
        long total = lines.stream().mapToLong(PhantomLine::quantity).sum();
        return new LotPhantomReport(lotId, total, lines);
    }

    /**
     * Writes off the lot's phantom stock as shrinkage — one append-only negative ledger movement
     * per phantom batch — so system stock equals the physical count. A no-op when there is nothing
     * phantom.
     */
    @Transactional
    public WriteOffResult writeOff(UUID lotId, Instant at) {
        long written = 0;
        for (Batch batch : batches.findByLotId(lotId)) {
            if (batch.getProduct().isPriced()) {
                continue;
            }
            long onHand = stockLevels.onHandForBatch(batch.getId());
            if (onHand > 0) {
                ledger.save(StockLedgerEntry.writeOff(batch.getProduct(), batch, onHand, at));
                written += onHand;
            }
        }
        return new WriteOffResult(lotId, written);
    }
}
