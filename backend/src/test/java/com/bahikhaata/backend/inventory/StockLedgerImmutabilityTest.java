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
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The ledger cannot be rewritten, at either layer.
 *
 * <p>Not transactional: these cases need rows genuinely committed, because a rollback would
 * hide whether the database actually refused the write. Each test uses its own product so
 * they do not interfere.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-ledger-immutability.db")
class StockLedgerImmutabilityTest {

    @Autowired
    private ProductRepository products;

    @Autowired
    private LotRepository lots;

    @Autowired
    private BatchRepository batches;

    @Autowired
    private StockLedgerRepository ledger;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactions;

    private StockLedgerEntry appendReceipt(String productName, long quantity) {
        Product product = products.save(new Product(productName, Category.of("KITCHEN"), Map.of()));
        Lot lot =
                lots.save(
                        new Lot(
                                "Liquidator A",
                                LocalDate.of(2026, 7, 20),
                                Money.ofRupees(50_000),
                                Money.ZERO,
                                AllocationMethod.RELATIVE_MRP));
        Batch batch =
                batches.save(
                        new Batch(
                                product, lot, Money.ofPaise(18_750), CostBasis.ALLOCATED,
                                quantity, 0, Money.ofRupees(300), false));

        return ledger.save(
                StockLedgerEntry.receipt(
                        product, batch, quantity, Instant.parse("2026-07-20T10:00:00Z")));
    }

    @Test
    @DisplayName("The database refuses an UPDATE, whatever issues it")
    void databaseRefusesUpdate() {
        UUID id = appendReceipt("Kettle for update", 100).getId();

        // Raw JDBC, bypassing Hibernate entirely: the specs require the *database* to refuse
        // this, so a test going through the ORM would prove the wrong thing.
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE stock_ledger SET quantity = 999 WHERE id = ?",
                                        id.toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT quantity FROM stock_ledger WHERE id = ?",
                                Long.class,
                                id.toString()))
                .isEqualTo(100L);
    }

    @Test
    @DisplayName("The database refuses a DELETE, and the row remains")
    void databaseRefusesDelete() {
        UUID id = appendReceipt("Kettle for delete", 50).getId();

        assertThatThrownBy(
                        () -> jdbc.update("DELETE FROM stock_ledger WHERE id = ?", id.toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM stock_ledger WHERE id = ?",
                                Integer.class,
                                id.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Hibernate's dirty checking never emits an UPDATE for a ledger row")
    void dirtyCheckingEmitsNoUpdate() {
        UUID id = appendReceipt("Kettle for dirty check", 30).getId();

        // The mutate-and-flush must happen inside a real transaction for dirty checking to
        // run at all; the class stays non-transactional so the surrounding rows genuinely
        // commit and the database guards are exercised for real.
        transactions.executeWithoutResult(
                status -> {
                    StockLedgerEntry loaded = entityManager.find(StockLedgerEntry.class, id);

                    // The entity exposes no setter, so reach the field directly — simulating a
                    // future change that mutates it however it manages to. @Immutable must stop
                    // Hibernate noticing. Without it the flush would emit an UPDATE and the
                    // trigger would throw, so a clean flush is itself the proof.
                    try {
                        Field quantity = StockLedgerEntry.class.getDeclaredField("quantity");
                        quantity.setAccessible(true);
                        quantity.setLong(loaded, 999L);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }

                    entityManager.flush();
                });

        assertThat(ledger.findById(id).orElseThrow().getQuantity()).isEqualTo(30L);
    }

}
