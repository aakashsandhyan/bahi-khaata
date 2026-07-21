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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-stock-levels.db")
@Transactional
class StockLevelsTest {

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

    private Product newProduct(String name) {
        return products.save(new Product(name, Category.of("KITCHEN"), Map.of()));
    }

    private Lot newLot(LocalDate receivedOn) {
        return lots.save(
                new Lot(
                        "Liquidator A",
                        receivedOn,
                        Money.ofRupees(50_000),
                        Money.ZERO,
                        AllocationMethod.RELATIVE_MRP));
    }

    private Batch newBatch(Product product, Lot lot, long received, long damaged) {
        return batches.save(
                new Batch(
                        product, lot, Money.ofPaise(18_750), CostBasis.ALLOCATED,
                        received, damaged, Money.ofRupees(300), false));
    }

    @Test
    @DisplayName("On hand is the net of a product's movements")
    void onHandIsNetOfMovements() {
        Product product = newProduct("Steel kettle");
        Batch batch = newBatch(product, newLot(LocalDate.of(2026, 7, 1)), 100, 0);

        ledger.save(StockLedgerEntry.receiptOf(batch, WHEN));
        ledger.save(StockLedgerEntry.sale(product, batch, 2, Money.ofPaise(37_500), WHEN));
        ledger.save(StockLedgerEntry.writeOff(product, batch, 1, WHEN));
        ledger.flush();

        assertThat(stock.onHand(product.getId())).isEqualTo(97);
    }

    @Test
    @DisplayName("Units damaged on arrival never count as stock on hand")
    void damagedOnArrivalNeverBecomesStock() {
        Product product = newProduct("Chipped bowls");
        // 100 arrived, 2 damaged — only 98 ever became sellable stock.
        Batch batch = newBatch(product, newLot(LocalDate.of(2026, 7, 1)), 100, 2);

        ledger.save(StockLedgerEntry.receiptOf(batch, WHEN));
        ledger.flush();

        assertThat(batch.getQuantityReceived()).isEqualTo(100);
        assertThat(stock.onHand(product.getId())).isEqualTo(98);
    }

    @Test
    @DisplayName("On hand is reported per batch, so FIFO knows what is left of each")
    void onHandIsReportedPerBatch() {
        Product product = newProduct("Steel bottle");
        Batch june = newBatch(product, newLot(LocalDate.of(2026, 6, 1)), 10, 0);
        Batch july = newBatch(product, newLot(LocalDate.of(2026, 7, 1)), 20, 0);

        ledger.save(StockLedgerEntry.receiptOf(june, WHEN));
        ledger.save(StockLedgerEntry.receiptOf(july, WHEN));
        ledger.save(StockLedgerEntry.sale(product, june, 4, Money.ofPaise(100), WHEN));
        ledger.flush();

        assertThat(stock.onHandForBatch(june.getId())).isEqualTo(6);
        assertThat(stock.onHandForBatch(july.getId())).isEqualTo(20);
        // The product total is the sum of what remains in each batch.
        assertThat(stock.onHand(product.getId())).isEqualTo(26);
    }

    @Test
    @DisplayName("A product that has never moved reports zero, not null")
    void neverMovedReportsZero() {
        Product product = newProduct("Never stocked");

        assertThat(stock.onHand(product.getId())).isZero();
    }

    @Test
    @DisplayName("Stock fully sold out reports zero")
    void soldOutReportsZero() {
        Product product = newProduct("Sold out");
        Batch batch = newBatch(product, newLot(LocalDate.of(2026, 7, 1)), 5, 0);

        ledger.save(StockLedgerEntry.receiptOf(batch, WHEN));
        ledger.save(StockLedgerEntry.sale(product, batch, 5, Money.ofPaise(100), WHEN));
        ledger.flush();

        assertThat(stock.onHandForBatch(batch.getId())).isZero();
        assertThat(stock.onHand(product.getId())).isZero();
    }
}
