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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.Money;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-lot-batch.db")
@Transactional
class LotBatchMappingTest {

    @Autowired
    private ProductRepository products;

    @Autowired
    private LotRepository lots;

    @Autowired
    private BatchRepository batches;

    private Lot newLot(LocalDate receivedOn) {
        return lots.save(
                new Lot(
                        "Liquidator A",
                        receivedOn,
                        Money.ofRupees(50_000),
                        Money.ofRupees(2_000),
                        AllocationMethod.RELATIVE_MRP));
    }

    private Product newProduct(String name) {
        return products.save(new Product(name, Category.of("KITCHEN"), Map.of()));
    }

    @Test
    @DisplayName("A lot round-trips, and the amount to allocate includes freight")
    void lotRoundTrips() {
        Lot saved = newLot(LocalDate.of(2026, 7, 20));

        Lot found = lots.findById(saved.getId()).orElseThrow();
        assertThat(found.getSupplier()).isEqualTo("Liquidator A");
        assertThat(found.getReceivedOn()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(found.getAllocationMethod()).isEqualTo(AllocationMethod.RELATIVE_MRP);
        // Freight is part of landed cost; leaving it out would understate every unit.
        assertThat(found.totalToAllocate()).isEqualTo(Money.ofRupees(52_000));
    }

    @Test
    @DisplayName("A batch round-trips with its cost, basis, MRP, and quantities")
    void batchRoundTrips() {
        Lot lot = newLot(LocalDate.of(2026, 7, 20));
        Product product = newProduct("Steel kettle");

        Batch saved =
                batches.save(
                        new Batch(
                                product,
                                lot,
                                Money.ofPaise(18_750),
                                CostBasis.ALLOCATED,
                                100,
                                2,
                                Money.ofRupees(300),
                                false));

        Batch found = batches.findById(saved.getId()).orElseThrow();
        assertThat(found.getAllocatedUnitCost()).isEqualTo(Money.ofPaise(18_750));
        assertThat(found.getCostBasis()).isEqualTo(CostBasis.ALLOCATED);
        assertThat(found.getMrp()).isEqualTo(Money.ofRupees(300));
        assertThat(found.isMrpEstimate()).isFalse();
        assertThat(found.getQuantityReceived()).isEqualTo(100);
        assertThat(found.getQuantityDamaged()).isEqualTo(2);
        // Damaged units are received but not sellable.
        assertThat(found.sellableQuantity()).isEqualTo(98);
    }

    @Test
    @DisplayName("Impossible quantities are refused before they reach the database")
    void impossibleQuantitiesRefused() {
        Lot lot = newLot(LocalDate.of(2026, 7, 20));
        Product product = newProduct("Bowl");

        assertThatThrownBy(
                        () ->
                                new Batch(
                                        product, lot, Money.ofPaise(1), CostBasis.ALLOCATED,
                                        0, 0, Money.ofRupees(10), false))
                .isInstanceOf(IllegalArgumentException.class);

        // More damaged than arrived would make the sellable quantity negative, inverting
        // the cost allocation.
        assertThatThrownBy(
                        () ->
                                new Batch(
                                        product, lot, Money.ofPaise(1), CostBasis.ALLOCATED,
                                        5, 9, Money.ofRupees(10), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Batches come back in FIFO order by the lot's delivery date, not row age")
    void fifoOrderFollowsDeliveryDate() {
        Product product = newProduct("Steel bottle");

        // The later-created row has the earlier delivery date — a delivery logged late.
        Lot june = newLot(LocalDate.of(2026, 6, 1));
        Lot july = newLot(LocalDate.of(2026, 7, 1));
        batches.save(
                new Batch(product, july, Money.ofPaise(200), CostBasis.ALLOCATED, 10, 0,
                        Money.ofRupees(50), false));
        batches.save(
                new Batch(product, june, Money.ofPaise(100), CostBasis.ALLOCATED, 10, 0,
                        Money.ofRupees(50), false));
        batches.flush();

        assertThat(batches.findByProductIdInFifoOrder(product.getId()))
                .extracting(Batch::getAllocatedUnitCost)
                // June's batch first, despite being created second.
                .containsExactly(Money.ofPaise(100), Money.ofPaise(200));
    }
}
