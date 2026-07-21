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

/**
 * Any past stock position can be reconstructed from the ledger, and valued at the cost of the
 * batches it actually consisted of.
 *
 * <p>Nothing is remembered — no month-end snapshot, no stored valuation. Every movement is
 * kept with the time it took effect, so a position is recomputed on demand, and a backdated
 * entry correctly changes what a past date reports.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-valuation.db")
@Transactional
class PointInTimeValuationTest {

    private static final Instant JUNE_10 = Instant.parse("2026-06-10T10:00:00Z");
    private static final Instant JULY_05 = Instant.parse("2026-07-05T10:00:00Z");
    private static final Instant JULY_20 = Instant.parse("2026-07-20T10:00:00Z");
    private static final Instant AUGUST_01 = Instant.parse("2026-08-01T10:00:00Z");

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

    private Batch newBatch(Product product, LocalDate receivedOn, long quantity, Money unitCost) {
        Lot lot =
                lots.save(
                        new Lot(
                                "Liquidator",
                                receivedOn,
                                Money.ofRupees(10_000),
                                Money.ZERO,
                                AllocationMethod.RELATIVE_MRP));
        return batches.save(
                new Batch(
                        product, lot, unitCost, CostBasis.ALLOCATED,
                        quantity, 0, Money.ofRupees(300), false));
    }

    @Test
    @DisplayName("On hand as at a past date excludes movements effective after it")
    void onHandAsAtExcludesLaterMovements() {
        Product product = newProduct("Steel kettle");
        Batch batch = newBatch(product, LocalDate.of(2026, 6, 10), 100, Money.ofRupees(120));

        ledger.save(StockLedgerEntry.receiptOf(batch, JUNE_10));
        ledger.save(StockLedgerEntry.sale(product, batch, 30, Money.ofRupees(3_600), JULY_20));
        ledger.flush();

        // Before the sale: all hundred were still on the shelf.
        assertThat(stock.onHandAsAt(product.getId(), JULY_05)).isEqualTo(100);
        // After it: seventy.
        assertThat(stock.onHandAsAt(product.getId(), AUGUST_01)).isEqualTo(70);
        assertThat(stock.onHand(product.getId())).isEqualTo(70);
    }

    @Test
    @DisplayName("A date before anything happened reports nothing")
    void beforeAnyMovementIsZero() {
        Product product = newProduct("Not yet stocked");
        Batch batch = newBatch(product, LocalDate.of(2026, 7, 20), 10, Money.ofRupees(120));
        ledger.save(StockLedgerEntry.receiptOf(batch, JULY_20));
        ledger.flush();

        assertThat(stock.onHandAsAt(product.getId(), JUNE_10)).isZero();
        assertThat(stock.valuationAsAt(product.getId(), JUNE_10)).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("A movement exactly on the boundary is included")
    void boundaryIsInclusive() {
        Product product = newProduct("Boundary");
        Batch batch = newBatch(product, LocalDate.of(2026, 7, 20), 10, Money.ofRupees(120));
        ledger.save(StockLedgerEntry.receiptOf(batch, JULY_20));
        ledger.flush();

        // "As at the 20th" means as at the end of that instant, so a movement effective at
        // exactly that instant counts.
        assertThat(stock.onHandAsAt(product.getId(), JULY_20)).isEqualTo(10);
    }

    @Test
    @DisplayName("Valuation uses each batch's own cost, not an average")
    void valuationIsPerBatch() {
        Product product = newProduct("Steel bottle");
        Batch june = newBatch(product, LocalDate.of(2026, 6, 10), 10, Money.ofRupees(120));
        Batch july = newBatch(product, LocalDate.of(2026, 7, 5), 10, Money.ofRupees(150));

        ledger.save(StockLedgerEntry.receiptOf(june, JUNE_10));
        ledger.save(StockLedgerEntry.receiptOf(july, JULY_05));
        ledger.flush();

        // 10 × ₹120 + 10 × ₹150 — an average would give the same total here, but would stop
        // the value being attributable to a delivery.
        assertThat(stock.valuationAsAt(product.getId(), AUGUST_01))
                .isEqualTo(Money.ofRupees(2_700));
    }

    @Test
    @DisplayName("Valuation as at a past date reflects what was held then, at then-costs")
    void valuationReflectsThePastPosition() {
        Product product = newProduct("Steel bottle");
        Batch june = newBatch(product, LocalDate.of(2026, 6, 10), 10, Money.ofRupees(120));
        Batch july = newBatch(product, LocalDate.of(2026, 7, 5), 10, Money.ofRupees(150));

        ledger.save(StockLedgerEntry.receiptOf(june, JUNE_10));
        ledger.save(StockLedgerEntry.receiptOf(july, JULY_05));
        ledger.save(StockLedgerEntry.sale(june.getProduct(), june, 10, Money.ofRupees(1_200), JULY_20));
        ledger.flush();

        // In late June only June's cheaper batch existed.
        assertThat(stock.valuationAsAt(product.getId(), Instant.parse("2026-06-20T10:00:00Z")))
                .isEqualTo(Money.ofRupees(1_200));
        // After July's arrival but before the sale: both batches.
        assertThat(stock.valuationAsAt(product.getId(), Instant.parse("2026-07-10T10:00:00Z")))
                .isEqualTo(Money.ofRupees(2_700));
        // After the sale emptied June's batch: only July's remains.
        assertThat(stock.valuationAsAt(product.getId(), AUGUST_01))
                .isEqualTo(Money.ofRupees(1_500));
    }

    @Test
    @DisplayName("A backdated entry changes what a past date reports")
    void backdatedEntryChangesThePast() {
        Product product = newProduct("Recount");
        Batch batch = newBatch(product, LocalDate.of(2026, 6, 10), 100, Money.ofRupees(120));
        ledger.save(StockLedgerEntry.receiptOf(batch, JUNE_10));
        ledger.flush();

        assertThat(stock.onHandAsAt(product.getId(), JULY_05)).isEqualTo(100);

        // A shortfall discovered later, effective back in early July.
        ledger.save(StockLedgerEntry.adjustment(product, batch, -5, JULY_05));
        ledger.flush();

        // The past position is recomputed, not corrected by hand.
        assertThat(stock.onHandAsAt(product.getId(), JULY_05)).isEqualTo(95);
        assertThat(stock.valuationAsAt(product.getId(), JULY_05)).isEqualTo(Money.ofRupees(11_400));
    }
}
