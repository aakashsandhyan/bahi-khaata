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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.bahikhaata.contracts.LotCostReconciliation;
import com.bahikhaata.contracts.Money;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The amount-paid cross-check: what was paid against the sum of the pinned costs. A reporting
 * figure that surfaces a mismatch — never a costing step that divides the amount into the goods.
 */
@ExtendWith(MockitoExtension.class)
class LotCostReconciliationTest {

    @Mock private LotRepository lots;
    @Mock private BatchRepository batches;
    @Mock private ExpectedLineRepository expectedLines;
    @Mock private UnlistedFindRepository unlistedFinds;
    @Mock private BoxRepository boxes;
    @Mock private ReceivingService receivingService;

    private LotClosing closing() {
        return new LotClosing(lots, batches, expectedLines, unlistedFinds, boxes, receivingService);
    }

    private Batch costedBatch(long unitCostPaise, long quantity) {
        Batch b = org.mockito.Mockito.mock(Batch.class);
        when(b.isCosted()).thenReturn(true);
        when(b.getAllocatedUnitCost()).thenReturn(Money.ofPaise(unitCostPaise));
        when(b.getQuantityReceived()).thenReturn(quantity);
        return b;
    }

    private void stubLot(UUID lotId, long amountPaidPaise, List<Batch> lotBatches) {
        Lot lot = org.mockito.Mockito.mock(Lot.class);
        when(lot.getAmountPaid()).thenReturn(Money.ofPaise(amountPaidPaise));
        when(lots.findById(lotId)).thenReturn(Optional.of(lot));
        when(batches.findByLotId(lotId)).thenReturn(lotBatches);
    }

    @Test
    void reconcilesWhenPinnedCostsSumToTheAmountPaid() {
        UUID lotId = UUID.randomUUID();
        // 400×1 + 5000×12 = 400 + 60000... use figures that sum to the paid amount: 40000 + 60000.
        stubLot(lotId, 100_000L, List.of(costedBatch(40_000L, 1), costedBatch(5_000L, 12)));

        LotCostReconciliation r = closing().crossCheckCost(lotId);

        assertThat(r.pinnedTotalPaise()).isEqualTo(100_000L);
        assertThat(r.differencePaise()).isZero();
        assertThat(r.reconciles()).isTrue();
    }

    @Test
    void reportsAMismatchRatherThanAbsorbingIt() {
        UUID lotId = UUID.randomUUID();
        stubLot(lotId, 100_000L, List.of(costedBatch(40_000L, 2))); // pinned 80000 vs paid 100000

        LotCostReconciliation r = closing().crossCheckCost(lotId);

        assertThat(r.pinnedTotalPaise()).isEqualTo(80_000L);
        assertThat(r.differencePaise()).isEqualTo(20_000L);
        assertThat(r.reconciles()).isFalse();
    }

    @Test
    void anUncostedSurplusContributesNothingToThePinnedTotal() {
        UUID lotId = UUID.randomUUID();
        Batch surplus = org.mockito.Mockito.mock(Batch.class);
        when(surplus.isCosted()).thenReturn(false);
        lenient().when(surplus.getAllocatedUnitCost()).thenReturn(null);
        stubLot(lotId, 40_000L, List.of(costedBatch(40_000L, 1), surplus));

        LotCostReconciliation r = closing().crossCheckCost(lotId);

        assertThat(r.pinnedTotalPaise()).isEqualTo(40_000L);
    }
}
