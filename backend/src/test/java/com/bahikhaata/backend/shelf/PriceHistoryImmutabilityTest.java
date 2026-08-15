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
package com.bahikhaata.backend.shelf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.Money;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code price_history} cannot be rewritten, at either layer — the same guarantee {@link
 * com.bahikhaata.backend.inventory.StockLedgerImmutabilityTest} proves for the stock ledger, and
 * the same reason: V45's trigger pair mirrors V6's exactly.
 *
 * <p>Not transactional: these cases need rows genuinely committed, because a rollback would hide
 * whether the database actually refused the write.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-price-history-immutability.db")
class PriceHistoryImmutabilityTest {

    @Autowired private ProductRepository products;
    @Autowired private ProductPricing pricing;
    @Autowired private PriceHistoryRepository priceHistory;
    @Autowired private JdbcTemplate jdbc;

    private UUID appendPriceChange(String productName, long pricePaise) {
        Product product = products.save(new Product(productName, Category.of("KITCHEN"), Map.of()));
        pricing.setSellingPrice(product.getId(), Money.ofPaise(pricePaise));
        return priceHistory.findByProductIdOrderByCreatedAtDesc(product.getId()).get(0).getId();
    }

    @Test
    @DisplayName("The database refuses an UPDATE, whatever issues it")
    void databaseRefusesUpdate() {
        UUID id = appendPriceChange("Kettle for update", 10000);

        // Raw JDBC, bypassing Hibernate entirely: the specs require the *database* to refuse
        // this, so a test going through the ORM would prove the wrong thing.
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE price_history SET new_price_paise = 999 WHERE id = ?",
                                        id.toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT new_price_paise FROM price_history WHERE id = ?",
                                Long.class,
                                id.toString()))
                .isEqualTo(10000L);
    }

    @Test
    @DisplayName("The database refuses a DELETE, and the row remains")
    void databaseRefusesDelete() {
        UUID id = appendPriceChange("Kettle for delete", 20000);

        assertThatThrownBy(
                        () -> jdbc.update("DELETE FROM price_history WHERE id = ?", id.toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM price_history WHERE id = ?",
                                Integer.class,
                                id.toString()))
                .isEqualTo(1);
    }
}
