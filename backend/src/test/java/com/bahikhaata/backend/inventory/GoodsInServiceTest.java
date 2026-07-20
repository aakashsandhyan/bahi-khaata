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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CostBasis;
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

@SpringBootTest(properties = "bahikhaata.db.path=build/test-goods-in.db")
@Transactional
class GoodsInServiceTest {

    @Autowired
    private ProductRepository products;

    @Autowired
    private BatchRepository batches;

    @Autowired
    private StockLevels stock;

    @Autowired
    private GoodsInService goodsIn;

    private Product newProduct(String name) {
        return products.save(new Product(name, Category.KITCHEN, Map.of()));
    }

    private static ReceiveLotLine line(Product product, long quantity, long mrpRupees) {
        return new ReceiveLotLine(
                product.getId().toString(), quantity, 0, mrpRupees * 100, false, null);
    }

    @Test
    @DisplayName("Receiving a mixed pallet costs each line by its retail value and brings stock in")
    void receivesAndAllocates() {
        Product bottle = newProduct("Steel bottle");
        Product kettle = newProduct("Electric kettle");
        Product keychain = newProduct("Keychain");

        var received =
                goodsIn.receive(
                        new ReceiveLotRequest(
                                "Liquidator A",
                                "2026-07-20",
                                50_000_00,
                                0,
                                List.of(
                                        line(bottle, 100, 300),
                                        line(kettle, 50, 800),
                                        line(keychain, 200, 50))));

        assertThat(received.allocation().method()).isEqualTo(AllocationMethod.RELATIVE_MRP);
        assertThat(received.batches())
                .extracting(Batch::getAllocatedUnitCost)
                .containsExactly(
                        Money.ofPaise(18_750), Money.ofRupees(500), Money.ofPaise(3_125));

        // The stock is on hand in the same breath as the costing.
        assertThat(stock.onHand(bottle.getId())).isEqualTo(100);
        assertThat(stock.onHand(kettle.getId())).isEqualTo(50);
        assertThat(stock.onHand(keychain.getId())).isEqualTo(200);
    }

    @Test
    @DisplayName("The batch shares of a lot sum to exactly what was paid, freight included")
    void sharesReconcileToTheLotAmount() {
        Product a = newProduct("A");
        Product b = newProduct("B");
        Product c = newProduct("C");

        var received =
                goodsIn.receive(
                        new ReceiveLotRequest(
                                "Liquidator A",
                                "2026-07-20",
                                33_333_33,
                                1_234_56,
                                List.of(line(a, 7, 111), line(b, 13, 222), line(c, 29, 333))));

        Money summed =
                received.batches().stream()
                        .map(Batch::getAllocatedTotal)
                        .reduce(Money.ZERO, Money::plus);

        assertThat(summed).isEqualTo(Money.ofPaise(33_333_33 + 1_234_56));
    }

    @Test
    @DisplayName("Stock arrives effective at the delivery date, not when it was entered")
    void stockArrivesAtTheDeliveryDate() {
        Product product = newProduct("Late entry");

        goodsIn.receive(
                new ReceiveLotRequest(
                        "Liquidator A", "2026-06-01", 10_000_00, 0, List.of(line(product, 10, 100))));

        // Entered today, effective in June — so FIFO consumes it in true arrival order and a
        // June valuation sees it.
        assertThat(stock.onHandAsAt(product.getId(), java.time.Instant.parse("2026-06-15T00:00:00Z")))
                .isEqualTo(10);
    }

    @Test
    @DisplayName("Damaged units are recorded but never become sellable stock")
    void damagedUnitsDoNotBecomeStock() {
        Product product = newProduct("Chipped bowls");

        var received =
                goodsIn.receive(
                        new ReceiveLotRequest(
                                "Liquidator A",
                                "2026-07-20",
                                10_000_00,
                                0,
                                List.of(
                                        new ReceiveLotLine(
                                                product.getId().toString(), 100, 20, 100_00, false, null))));

        Batch batch = received.batches().get(0);
        assertThat(batch.getQuantityReceived()).isEqualTo(100);
        assertThat(batch.getQuantityDamaged()).isEqualTo(20);
        // The whole amount is still allocated, spread over the 80 that can be sold.
        assertThat(batch.getAllocatedTotal()).isEqualTo(Money.ofRupees(10_000));
        assertThat(batch.getAllocatedUnitCost()).isEqualTo(Money.ofRupees(125));
        assertThat(stock.onHand(product.getId())).isEqualTo(80);
    }

    @Test
    @DisplayName("A pinned line records its basis, and shifts what the others receive")
    void pinnedLineRecordsItsBasis() {
        Product bottle = newProduct("Steel bottle");
        Product kettle = newProduct("Electric kettle");

        var received =
                goodsIn.receive(
                        new ReceiveLotRequest(
                                "Liquidator A",
                                "2026-07-20",
                                50_000_00,
                                0,
                                List.of(
                                        line(bottle, 100, 300),
                                        new ReceiveLotLine(
                                                kettle.getId().toString(), 50, 0, 800_00, false, 450_00L))));

        assertThat(received.batches())
                .extracting(Batch::getCostBasis)
                .containsExactly(CostBasis.ALLOCATED, CostBasis.PINNED);
        assertThat(received.batches().get(1).getAllocatedTotal()).isEqualTo(Money.ofRupees(22_500));
        // The bottles absorb the rest, rather than keeping the share they would have had.
        assertThat(received.batches().get(0).getAllocatedTotal()).isEqualTo(Money.ofRupees(27_500));
    }

    @Test
    @DisplayName("An unknown product is refused before anything is written")
    void unknownProductIsRefused() {
        long lotsBefore = batches.count();

        assertThatThrownBy(
                        () ->
                                goodsIn.receive(
                                        new ReceiveLotRequest(
                                                "Liquidator A",
                                                "2026-07-20",
                                                10_000_00,
                                                0,
                                                List.of(
                                                        new ReceiveLotLine(
                                                                UUID.randomUUID().toString(),
                                                                10, 0, 100_00, false, null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no such product");

        assertThat(batches.count()).isEqualTo(lotsBefore);
    }

    @Test
    @DisplayName("The same product twice in one delivery is refused, not silently split")
    void duplicateProductLineIsRefused() {
        Product product = newProduct("Duplicated");

        // Two lines would halve its allocation and violate the one-line-per-product-per-lot
        // constraint — better to say so than to fail on a database error later.
        assertThatThrownBy(
                        () ->
                                goodsIn.receive(
                                        new ReceiveLotRequest(
                                                "Liquidator A",
                                                "2026-07-20",
                                                10_000_00,
                                                0,
                                                List.of(line(product, 10, 100), line(product, 5, 100)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than one line");
    }

    @Test
    @DisplayName("A delivery that cannot be allocated writes nothing at all")
    void failedAllocationWritesNothing() {
        Product product = newProduct("Overpinned");
        long batchesBefore = batches.count();

        assertThatThrownBy(
                        () ->
                                goodsIn.receive(
                                        new ReceiveLotRequest(
                                                "Liquidator A",
                                                "2026-07-20",
                                                1_000_00,
                                                0,
                                                List.of(
                                                        new ReceiveLotLine(
                                                                product.getId().toString(),
                                                                10, 0, 100_00, false, 500_00L)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the lot amount");

        // No half-received delivery: allocation runs before anything is persisted.
        assertThat(batches.count()).isEqualTo(batchesBefore);
        assertThat(stock.onHand(product.getId())).isZero();
    }
}
