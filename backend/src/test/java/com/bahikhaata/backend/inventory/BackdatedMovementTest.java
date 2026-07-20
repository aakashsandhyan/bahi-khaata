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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * A movement can be recorded with an effective time earlier than movements already in the
 * ledger — a delivery logged two days late, a sale entered after the fact.
 *
 * <p>Nothing is recalculated and nothing is rewritten. Because quantity on hand is derived by
 * summing movements rather than stored as a counter, appending an earlier row simply changes
 * what every later query computes. That is the whole payoff of deriving rather than storing:
 * backdating needs no correction pass, and history stays exactly as it was written.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-backdated.db")
@Transactional
class BackdatedMovementTest {

    private static final Instant JULY_20 = Instant.parse("2026-07-20T10:00:00Z");
    private static final Instant JULY_18 = Instant.parse("2026-07-18T09:00:00Z");

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
    private JdbcTemplate jdbc;

    private Product newProduct(String name) {
        return products.save(new Product(name, Category.KITCHEN, Map.of()));
    }

    private Batch newBatch(Product product, LocalDate receivedOn, long quantity) {
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
                        product, lot, Money.ofRupees(120), CostBasis.ALLOCATED,
                        quantity, 0, Money.ofRupees(300), false));
    }

    /** Every column of every ledger row, so a change anywhere would show. */
    private List<Map<String, Object>> ledgerSnapshot() {
        return jdbc.queryForList(
                "SELECT id, product_id, batch_id, quantity, movement_type, cogs_paise, "
                        + "effective_at, created_at FROM stock_ledger ORDER BY id");
    }

    @Test
    @DisplayName("A movement effective earlier than existing ones is accepted")
    void earlierMovementIsAccepted() {
        Product product = newProduct("Steel kettle");
        Batch batch = newBatch(product, LocalDate.of(2026, 7, 18), 100);

        ledger.save(StockLedgerEntry.sale(product, batch, 2, Money.ofRupees(240), JULY_20));
        ledger.flush();

        // The delivery actually arrived on the 18th but was only entered now, after a sale
        // dated the 20th had already been recorded.
        ledger.save(StockLedgerEntry.receiptOf(batch, JULY_18));
        ledger.flush();

        assertThat(ledger.findByBatchId(batch.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Appending a backdated movement modifies no existing row")
    void existingRowsAreUntouched() {
        Product product = newProduct("Steel bottle");
        Batch batch = newBatch(product, LocalDate.of(2026, 7, 18), 100);

        ledger.save(StockLedgerEntry.receiptOf(batch, JULY_20));
        ledger.save(StockLedgerEntry.sale(product, batch, 5, Money.ofRupees(600), JULY_20));
        ledger.flush();

        List<Map<String, Object>> before = ledgerSnapshot();

        ledger.save(StockLedgerEntry.adjustment(product, batch, -1, JULY_18));
        ledger.flush();

        List<Map<String, Object>> after = ledgerSnapshot();

        // History is not rewritten to accommodate the insertion: every row that existed
        // before is still present, byte for byte, with only the new row added.
        assertThat(after).hasSize(before.size() + 1);
        assertThat(after).containsAll(before);
    }

    @Test
    @DisplayName("Quantity on hand accounts for the backdated movement without a recalculation")
    void onHandAccountsForBackdatedMovement() {
        Product product = newProduct("Wall clock");
        Batch batch = newBatch(product, LocalDate.of(2026, 7, 18), 100);

        ledger.save(StockLedgerEntry.receiptOf(batch, JULY_20));
        ledger.flush();
        assertThat(stock.onHand(product.getId())).isEqualTo(100);

        // A shortfall found at a stock take, effective two days earlier.
        ledger.save(StockLedgerEntry.adjustment(product, batch, -4, JULY_18));
        ledger.flush();

        // No correction pass ran; the derived figure simply includes the new movement.
        assertThat(stock.onHand(product.getId())).isEqualTo(96);
    }

    @Test
    @DisplayName("A late-logged delivery still increases stock, and orders by effective time")
    void lateLoggedDeliveryOrdersByEffectiveTime() {
        Product product = newProduct("Gift box");
        Batch batch = newBatch(product, LocalDate.of(2026, 7, 18), 50);

        // Written second, but effective first.
        ledger.save(StockLedgerEntry.adjustment(product, batch, 1, JULY_20));
        ledger.flush();
        ledger.save(StockLedgerEntry.receiptOf(batch, JULY_18));
        ledger.flush();

        assertThat(stock.onHand(product.getId())).isEqualTo(51);
        assertThat(
                        ledger.findByProductIdOrderByEffectiveAtAscCreatedAtAsc(product.getId()))
                .extracting(StockLedgerEntry::getEffectiveAt)
                // Effective order, not the order the rows were written.
                .containsExactly(JULY_18, JULY_20);
    }
}
