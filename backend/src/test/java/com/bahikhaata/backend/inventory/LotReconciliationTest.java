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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.contracts.LotPhantomReport;
import com.bahikhaata.contracts.WriteOffResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LotReconciliationTest {

    @Mock private BatchRepository batches;
    @Mock private StockLevels stockLevels;
    @Mock private StockLedgerRepository ledger;

    private LotReconciliation reconciliation() {
        return new LotReconciliation(batches, stockLevels, ledger);
    }

    private Batch batchFor(Product product, UUID batchId) {
        Batch batch = mock(Batch.class);
        when(batch.getProduct()).thenReturn(product);
        lenient().when(batch.getId()).thenReturn(batchId);
        return batch;
    }

    @Test
    void phantomIsTheOnHandStockOfUnpricedBatches() {
        UUID lotId = UUID.randomUUID();
        Product priced = spy(new Product("Sold thing", com.bahikhaata.contracts.Category.of("KITCHEN"), java.util.Map.of()));
        priced.setSellingPrice(com.bahikhaata.contracts.Money.ofRupees(100));
        Product unpriced = new Product("Orphan", com.bahikhaata.contracts.Category.of("KITCHEN"), java.util.Map.of());

        UUID pricedBatchId = UUID.randomUUID();
        UUID phantomBatchId = UUID.randomUUID();
        Batch pricedBatch = batchFor(priced, pricedBatchId);
        Batch phantomBatch = batchFor(unpriced, phantomBatchId);
        when(batches.findByLotId(lotId)).thenReturn(List.of(pricedBatch, phantomBatch));
        when(stockLevels.onHandForBatch(phantomBatchId)).thenReturn(7L);

        LotPhantomReport report = reconciliation().phantomReport(lotId);

        // Only the unpriced batch's on-hand is phantom.
        assertThat(report.totalPhantom()).isEqualTo(7L);
        assertThat(report.lines()).singleElement()
                .satisfies(line -> assertThat(line.quantity()).isEqualTo(7L));
    }

    @Test
    void writeOffRemovesThePhantomAsAnAppendOnlyNegativeMovement() {
        UUID lotId = UUID.randomUUID();
        Product unpriced = new Product("Orphan", com.bahikhaata.contracts.Category.of("KITCHEN"), java.util.Map.of());
        UUID batchId = UUID.randomUUID();
        Batch batch = batchFor(unpriced, batchId);
        when(batches.findByLotId(lotId)).thenReturn(List.of(batch));
        when(stockLevels.onHandForBatch(batchId)).thenReturn(7L);

        WriteOffResult result = reconciliation().writeOff(lotId, Instant.now());

        assertThat(result.quantityWrittenOff()).isEqualTo(7L);
        ArgumentCaptor<StockLedgerEntry> entry = ArgumentCaptor.forClass(StockLedgerEntry.class);
        verify(ledger).save(entry.capture());
        // A write-off is a negative movement — stock leaving.
        assertThat(entry.getValue().getQuantity()).isNegative();
    }

    @Test
    void nothingPhantomIsANoOp() {
        UUID lotId = UUID.randomUUID();
        Product priced = new Product("Sold", com.bahikhaata.contracts.Category.of("KITCHEN"), java.util.Map.of());
        priced.setSellingPrice(com.bahikhaata.contracts.Money.ofRupees(100));
        Batch pricedBatch = batchFor(priced, UUID.randomUUID());
        when(batches.findByLotId(lotId)).thenReturn(List.of(pricedBatch));

        WriteOffResult result = reconciliation().writeOff(lotId, Instant.now());

        assertThat(result.quantityWrittenOff()).isZero();
        verifyNoInteractions(ledger);
    }
}
