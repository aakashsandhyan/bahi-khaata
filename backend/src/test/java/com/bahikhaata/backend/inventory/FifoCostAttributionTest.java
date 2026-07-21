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

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cost of goods sold is attributed at the cost of the batch actually consumed.
 *
 * <p>The point is not the total — averaging across lots gives the same total for a sale. The
 * point is <em>which lot the cost lands on</em>, so a per-lot margin can be read afterwards
 * and the question "was that supplier's pallet worth buying again?" has an answer.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-fifo-cost.db")
@Transactional
class FifoCostAttributionTest {

    private static final Instant WHEN = Instant.parse("2026-07-20T10:00:00Z");

    @Autowired
    private ProductRepository products;

    @Autowired
    private LotRepository lots;

    @Autowired
    private BatchRepository batches;

    @Autowired
    private StockLedgerRepository ledger;

    @Autowired
    private StockLevels stock;

    @Autowired
    private FifoConsumer fifo;

    private Product newProduct(String name) {
        return products.save(new Product(name, Category.of("KITCHEN"), Map.of()));
    }

    private Batch stockedBatch(Product product, LocalDate date, long quantity, Money unitCost) {
        Lot lot =
                lots.save(
                        new Lot(
                                "Liquidator",
                                date,
                                Money.ofRupees(10_000),
                                Money.ZERO,
                                AllocationMethod.RELATIVE_MRP));
        Batch batch =
                batches.save(
                        new Batch(
                                product, lot, unitCost, CostBasis.ALLOCATED,
                                quantity, 0, Money.ofRupees(300), false));
        ledger.save(StockLedgerEntry.receiptOf(batch, WHEN));
        ledger.flush();
        return batch;
    }

    @Test
    @DisplayName("A sale from one batch is costed at that batch's cost")
    void singleBatchSaleUsesItsOwnCost() {
        Product product = newProduct("Steel kettle");
        stockedBatch(product, LocalDate.of(2026, 6, 1), 10, Money.ofRupees(120));

        List<StockLedgerEntry> movements = fifo.consumeForSale(product.getId(), 2, WHEN);

        assertThat(movements).singleElement()
                .extracting(StockLedgerEntry::getCogs)
                .isEqualTo(Money.ofRupees(240));
    }

    @Test
    @DisplayName("A sale spanning two batches costs each portion at its own batch's cost")
    void spanningSaleCostsEachPortionSeparately() {
        Product product = newProduct("Steel bottle");
        // June's pallet was cheaper than July's — the difference must survive the sale.
        stockedBatch(product, LocalDate.of(2026, 6, 1), 3, Money.ofRupees(120));
        stockedBatch(product, LocalDate.of(2026, 7, 1), 10, Money.ofRupees(150));

        List<StockLedgerEntry> movements = fifo.consumeForSale(product.getId(), 5, WHEN);

        assertThat(movements)
                .extracting(StockLedgerEntry::getQuantity, StockLedgerEntry::getCogs)
                .containsExactly(
                        // three at ₹120
                        Tuple.tuple(-3L, Money.ofRupees(360)),
                        // two at ₹150
                        Tuple.tuple(-2L, Money.ofRupees(300)));
    }

    @Test
    @DisplayName("Per-lot margin is answerable: each batch carries its own realised cost")
    void perBatchCostIsAnswerable() {
        Product product = newProduct("Steel bottle");
        Batch june = stockedBatch(product, LocalDate.of(2026, 6, 1), 3, Money.ofRupees(120));
        Batch july = stockedBatch(product, LocalDate.of(2026, 7, 1), 10, Money.ofRupees(150));

        fifo.consumeForSale(product.getId(), 5, WHEN);
        ledger.flush();

        // This is what averaging would destroy: after the sale you can still say what June's
        // pallet cost to sell and what July's did, separately.
        assertThat(stock.costOfGoodsSoldForBatch(june.getId())).isEqualTo(Money.ofRupees(360));
        assertThat(stock.costOfGoodsSoldForBatch(july.getId())).isEqualTo(Money.ofRupees(300));
    }

    @Test
    @DisplayName("An arrival contributes no cost of goods sold")
    void receiptsContributeNoCost() {
        Product product = newProduct("Just received");
        Batch batch = stockedBatch(product, LocalDate.of(2026, 6, 1), 10, Money.ofRupees(120));

        // Received but nothing sold yet: cost of goods *sold* is zero, even though the stock
        // was paid for.
        assertThat(stock.costOfGoodsSoldForBatch(batch.getId())).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("Cost attribution is exact in paise, with no rounding drift")
    void costAttributionIsExact() {
        Product product = newProduct("Odd priced");
        // A cost that does not divide evenly into rupees.
        stockedBatch(product, LocalDate.of(2026, 6, 1), 7, Money.ofPaise(3_333));

        List<StockLedgerEntry> movements = fifo.consumeForSale(product.getId(), 3, WHEN);

        // 3 × 3333 paise exactly — integer arithmetic throughout, no fractional paise lost.
        assertThat(movements).singleElement()
                .extracting(StockLedgerEntry::getCogs)
                .isEqualTo(Money.ofPaise(9_999));
    }
}
