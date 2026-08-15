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

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.Money;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The journal write at {@link ProductPricing}'s choke point (design decision D5 of
 * palletworks-inventory): what gets appended, when nothing is appended, and what a first-ever set
 * looks like. Caller-specific journalling (workbench, ShelfPricing, the catalog controller) is
 * exercised where those callers already have their own integration tests; this class proves the
 * choke point's own rules directly, against a real database.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-price-history-journal.db")
@Transactional
class PriceHistoryJournalTest {

    @Autowired private ProductRepository products;
    @Autowired private ProductPricing pricing;
    @Autowired private PriceHistoryRepository priceHistory;
    @Autowired private JdbcTemplate jdbc;

    private Product freshProduct() {
        return products.save(new Product("Journal Kettle", Category.of("KITCHEN"), Map.of()));
    }

    @Test
    @DisplayName("A product's first-ever price set records a NULL old price")
    void firstSetRecordsNullOldPrice() {
        Product product = freshProduct();

        pricing.setSellingPrice(product.getId(), Money.ofRupees(300));

        List<PriceHistory> rows = priceHistory.findByProductIdOrderByCreatedAtDesc(product.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOldPrice()).as("nothing came before this").isNull();
        assertThat(rows.get(0).getNewPrice()).isEqualTo(Money.ofRupees(300));
    }

    @Test
    @DisplayName("A later change to a different price records the true old-to-new transition")
    void laterChangeRecordsOldToNew() {
        Product product = freshProduct();
        pricing.setSellingPrice(product.getId(), Money.ofRupees(300));

        pricing.setSellingPrice(product.getId(), Money.ofRupees(350));

        // Identified by content, not list position: two calls issued back to back can land in the
        // same created_at millisecond, so which row sorts first is not what this asserts — the
        // dedicated ordering test below proves the sort itself against distinguishable timestamps.
        List<PriceHistory> rows = priceHistory.findByProductIdOrderByCreatedAtDesc(product.getId());
        assertThat(rows).hasSize(2);
        PriceHistory firstSet = rows.stream().filter(h -> h.getOldPrice() == null).findFirst().orElseThrow();
        PriceHistory secondSet = rows.stream().filter(h -> h.getOldPrice() != null).findFirst().orElseThrow();
        assertThat(firstSet.getNewPrice()).isEqualTo(Money.ofRupees(300));
        assertThat(secondSet.getOldPrice()).isEqualTo(Money.ofRupees(300));
        assertThat(secondSet.getNewPrice()).isEqualTo(Money.ofRupees(350));
    }

    @Test
    @DisplayName("A set whose new price equals the current price is a no-op and journals nothing")
    void unchangedPriceIsNotJournaled() {
        Product product = freshProduct();
        pricing.setSellingPrice(product.getId(), Money.ofRupees(300));

        // Same figure again — an MRP or quantity-only correction, not a real re-price.
        pricing.setSellingPrice(product.getId(), Money.ofRupees(300));

        assertThat(priceHistory.findByProductIdOrderByCreatedAtDesc(product.getId()))
                .as("no second row for a price that did not actually change")
                .hasSize(1);
    }

    @Test
    @DisplayName("Price history reads newest-first")
    void readsNewestFirst() {
        // Rows inserted with explicit, distinguishable created_at values — raw JDBC, not the
        // choke point — so the sort is proven against real timestamp differences rather than
        // however close together two live pricing.setSellingPrice() calls happen to land.
        // saveAndFlush, not save: the raw JDBC insert below needs the product row physically in
        // the database to satisfy price_history's foreign key, and Hibernate would otherwise defer
        // the INSERT to end of transaction, invisible to a connection it does not know about.
        Product product = products.saveAndFlush(new Product("Journal Kettle", Category.of("KITCHEN"), Map.of()));
        insertHistoryRow(product.getId(), null, 10000L, "2026-08-01T09:00:00.000Z");
        insertHistoryRow(product.getId(), 10000L, 20000L, "2026-08-01T10:00:00.000Z");
        insertHistoryRow(product.getId(), 20000L, 30000L, "2026-08-01T11:00:00.000Z");

        List<PriceHistory> rows = priceHistory.findByProductIdOrderByCreatedAtDesc(product.getId());
        assertThat(rows).extracting(h -> h.getNewPrice().paise())
                .containsExactly(30000L, 20000L, 10000L);
    }

    private void insertHistoryRow(UUID productId, Long oldPaise, long newPaise, String createdAt) {
        jdbc.update(
                "INSERT INTO price_history (id, product_id, old_price_paise, new_price_paise, "
                        + "operator_name, created_at) VALUES (?, ?, ?, ?, NULL, ?)",
                UUID.randomUUID().toString(), productId.toString(), oldPaise, newPaise, createdAt);
    }
}
