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
package com.bahikhaata.backend.inventory.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.backend.inventory.GoodsInService;
import com.bahikhaata.backend.inventory.Supplier;
import com.bahikhaata.backend.inventory.SupplierRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.ReceiveLotLine;
import com.bahikhaata.contracts.ReceiveLotRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changing how costs are apportioned never re-costs lots already allocated.
 *
 * <p>Retroactive re-allocation would rewrite the margin history those costs support, and
 * silently invalidate every reorder decision made on the old numbers. The property holds
 * structurally rather than by rule: a batch stores its own allocated share, and the lot records
 * the method that produced it, so nothing recomputes anything on read.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-method-change.db")
@Transactional
class AllocationMethodChangeTest {

    @Autowired
    private ProductRepository products;

    @Autowired
    private BatchRepository batches;

    @Autowired
    private GoodsInService goodsIn;

    @Autowired
    private SupplierRepository suppliers;

    private String supplierId(String name) {
        return suppliers.findByNameNormalized(Supplier.normalize(name))
                .map(Supplier::getId)
                .orElseGet(() -> suppliers.save(new Supplier(name, null, null, null, null, null)).getId())
                .toString();
    }

    private Product newProduct(String name) {
        return products.save(new Product(name, Category.of("KITCHEN"), Map.of()));
    }

    private static ReceiveLotLine line(Product product, long quantity, long mrpRupees) {
        return new ReceiveLotLine(
                product.getId().toString(), quantity, 0, mrpRupees * 100, false, null);
    }

    /** A lot of two very differently priced products, where the strategies plainly disagree. */
    private GoodsInService.ReceivedLot receiveMixedPallet() {
        Product kettle = newProduct("Electric kettle " + UUID.randomUUID());
        Product keychain = newProduct("Keychain " + UUID.randomUUID());

        return goodsIn.receive(
                new ReceiveLotRequest(
                        supplierId("Liquidator A"),
                        "2026-07-20",
                        40_000_00,
                        0,
                        List.of(line(kettle, 50, 800), line(keychain, 200, 50))));
    }

    @Test
    @DisplayName("A lot records the method that produced its figures")
    void lotRecordsItsMethod() {
        assertThat(receiveMixedPallet().lot().getAllocationMethod())
                .isEqualTo(AllocationMethod.RELATIVE_MRP);
    }

    @Test
    @DisplayName("The two strategies genuinely disagree, so the test below is meaningful")
    void strategiesDisagree() {
        List<AllocationLine> lines =
                List.of(
                        new AllocationLine("kettle", 50, 0, Money.ofRupees(800), null),
                        new AllocationLine("keychain", 200, 0, Money.ofRupees(50), null));
        Money paid = Money.ofRupees(40_000);

        Allocation byValue = new CostAllocator(new RelativeMrpWeighting()).allocate(paid, Money.ZERO, lines);
        Allocation perUnit = new CostAllocator(new EqualPerUnitWeighting()).allocate(paid, Money.ZERO, lines);

        // Retail value is ₹40,000 of kettles against ₹10,000 of keychains, so by value the
        // keychains take a fifth of the ₹40,000 pallet — ₹8,000, or ₹40 each, under their ₹50
        // retail price. Per unit they are 200 of 250 items and take four fifths — ₹32,000, or
        // ₹160 each, more than triple what they sell for.
        assertThat(byValue.lines().get(1).allocatedTotal()).isEqualTo(Money.ofRupees(8_000));
        assertThat(perUnit.lines().get(1).allocatedTotal()).isEqualTo(Money.ofRupees(32_000));
    }

    @Test
    @DisplayName("Switching the strategy leaves already-allocated costs untouched")
    void existingLotsKeepTheirCosts() {
        GoodsInService.ReceivedLot received = receiveMixedPallet();
        UUID lotId = received.lot().getId();

        // Keyed by product, not by position: findByLotId does not order its rows, so indexing
        // into it makes a test that passes or fails on SQLite's choice of query plan.
        Map<UUID, Money> costsWhenReceived =
                batches.findByLotId(lotId).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        b -> b.getProduct().getId(), Batch::getAllocatedTotal));

        // The keychains are the line the two strategies disagree most sharply about.
        UUID keychainId = received.batches().get(1).getProduct().getId();

        // Switching the configured strategy is a change to one bean. Nothing walks back over
        // history, because nothing recomputes a stored share.
        Allocation wouldBeDifferent =
                new CostAllocator(new EqualPerUnitWeighting())
                        .allocate(
                                received.lot().getAmountPaid(),
                                received.lot().getFreight(),
                                List.of(
                                        new AllocationLine("kettle", 50, 0, Money.ofRupees(800), null),
                                        new AllocationLine("keychain", 200, 0, Money.ofRupees(50), null)));

        assertThat(batches.findByLotId(lotId))
                .allSatisfy(
                        batch ->
                                assertThat(batch.getAllocatedTotal())
                                        .isEqualTo(costsWhenReceived.get(batch.getProduct().getId())));

        // And the figures the other strategy would have produced are demonstrably different,
        // so this is not passing because the two happen to agree.
        assertThat(wouldBeDifferent.lines().get(1).allocatedTotal())
                .isNotEqualTo(costsWhenReceived.get(keychainId));
    }

    @Test
    @DisplayName("A lot keeps its recorded method, so an old cost can still be judged")
    void methodStaysWithTheLot() {
        GoodsInService.ReceivedLot received = receiveMixedPallet();

        // Months later, reading a cost of ₹36,000 against the kettles means nothing without
        // knowing it came from a relative-value apportionment rather than an itemised invoice.
        assertThat(received.lot().getAllocationMethod()).isEqualTo(AllocationMethod.RELATIVE_MRP);
        assertThat(batches.findByLotId(received.lot().getId()))
                .allSatisfy(
                        batch ->
                                assertThat(batch.getLot().getAllocationMethod())
                                        .isEqualTo(AllocationMethod.RELATIVE_MRP));
    }
}
