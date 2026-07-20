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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-fifo.db")
@Transactional
class FifoConsumerTest {

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
        return products.save(new Product(name, Category.KITCHEN, Map.of()));
    }

    /** A batch of {@code quantity} units at {@code unitCostPaise}, received on {@code date}. */
    private Batch stockedBatch(Product product, LocalDate date, long quantity, long unitCostPaise) {
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
                                product, lot, Money.ofPaise(unitCostPaise), CostBasis.ALLOCATED,
                                quantity, 0, Money.ofRupees(300), false));
        ledger.save(StockLedgerEntry.receiptOf(batch, WHEN));
        ledger.flush();
        return batch;
    }

    @Test
    @DisplayName("Consumption draws from the oldest batch with quantity remaining")
    void drawsFromOldestFirst() {
        Product product = newProduct("Steel kettle");
        Batch june = stockedBatch(product, LocalDate.of(2026, 6, 1), 10, 12_000);
        stockedBatch(product, LocalDate.of(2026, 7, 1), 10, 15_000);

        assertThat(fifo.plan(product.getId(), 4))
                .containsExactly(new BatchDraw(june, 4));
    }

    @Test
    @DisplayName("Consumption spans batches when the oldest is exhausted, recording each part")
    void spansBatchesWhenOldestExhausted() {
        Product product = newProduct("Steel bottle");
        Batch june = stockedBatch(product, LocalDate.of(2026, 6, 1), 3, 12_000);
        Batch july = stockedBatch(product, LocalDate.of(2026, 7, 1), 10, 15_000);

        // Five wanted, only three left in June: three from June, two from July.
        assertThat(fifo.plan(product.getId(), 5))
                .containsExactly(new BatchDraw(june, 3), new BatchDraw(july, 2));
    }

    @Test
    @DisplayName("An already-exhausted batch is skipped rather than drawn from")
    void skipsExhaustedBatches() {
        Product product = newProduct("Wall clock");
        Batch june = stockedBatch(product, LocalDate.of(2026, 6, 1), 2, 12_000);
        Batch july = stockedBatch(product, LocalDate.of(2026, 7, 1), 10, 15_000);

        fifo.consumeForSale(product.getId(), 2, WHEN); // empties June
        ledger.flush();

        assertThat(stock.onHandForBatch(june.getId())).isZero();
        assertThat(fifo.plan(product.getId(), 3)).containsExactly(new BatchDraw(july, 3));
    }

    @Test
    @DisplayName("Taking exactly what remains succeeds and leaves the batch at zero")
    void exactFitSucceeds() {
        Product product = newProduct("Gift box");
        Batch only = stockedBatch(product, LocalDate.of(2026, 6, 1), 5, 12_000);

        fifo.consumeForSale(product.getId(), 5, WHEN);
        ledger.flush();

        assertThat(stock.onHandForBatch(only.getId())).isZero();
        assertThat(stock.onHand(product.getId())).isZero();
    }

    @Test
    @DisplayName("Asking for more than is on hand is refused, naming what is available")
    void insufficientStockIsRefused() {
        Product product = newProduct("Scarce item");
        stockedBatch(product, LocalDate.of(2026, 6, 1), 3, 12_000);

        assertThatThrownBy(() -> fifo.plan(product.getId(), 5))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(
                        e -> {
                            InsufficientStockException short_ = (InsufficientStockException) e;
                            // The cashier needs to know which product, and by how much.
                            assertThat(short_.getProductId()).isEqualTo(product.getId());
                            assertThat(short_.getRequested()).isEqualTo(5);
                            assertThat(short_.getAvailable()).isEqualTo(3);
                        });
    }

    @Test
    @DisplayName("A refused consumption writes nothing")
    void refusedConsumptionWritesNothing() {
        Product product = newProduct("Untouched");
        Batch batch = stockedBatch(product, LocalDate.of(2026, 6, 1), 3, 12_000);

        assertThatThrownBy(() -> fifo.consumeForSale(product.getId(), 5, WHEN))
                .isInstanceOf(InsufficientStockException.class);
        ledger.flush();

        // Stock untouched: a failed sale must not have quietly consumed the batches it
        // could reach before running out.
        assertThat(stock.onHandForBatch(batch.getId())).isEqualTo(3);
    }

    @Test
    @DisplayName("Consuming appends one movement per batch drawn from")
    void consumingAppendsOneMovementPerBatch() {
        Product product = newProduct("Spanning sale");
        stockedBatch(product, LocalDate.of(2026, 6, 1), 3, 12_000);
        stockedBatch(product, LocalDate.of(2026, 7, 1), 10, 15_000);

        assertThat(fifo.consumeForSale(product.getId(), 5, WHEN))
                .hasSize(2)
                .extracting(StockLedgerEntry::getQuantity)
                // Recorded negative: stock leaving.
                .containsExactly(-3L, -2L);

        assertThat(stock.onHand(product.getId())).isEqualTo(8);
    }
}
