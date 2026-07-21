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
package com.bahikhaata.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.bahikhaata.contracts.Category;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The Product mapping persists and reloads faithfully. That the context starts at all means
 * the mapping validated against the reviewed V3 schema under {@code ddl-auto=validate}; these
 * cases confirm the parts a validator cannot check — that values survive a round trip.
 *
 * <p>JSON attributes get their own scenarios in the next task; here the concern is identity,
 * category, the unpriced state, and the timestamps.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-product-mapping.db")
@DirtiesContext
class ProductMappingTest {

    @Autowired
    private ProductRepository products;

    @Test
    @DisplayName("A new product round-trips with its category and identity intact")
    void roundTripsIdentityAndCategory() {
        Product saved = products.save(new Product("Steel kettle", Category.of("KITCHEN"), Map.of()));
        UUID id = saved.getId();

        Product found = products.findById(id).orElseThrow();
        assertThat(found.getName()).isEqualTo("Steel kettle");
        assertThat(found.getCategory()).isEqualTo(Category.of("KITCHEN"));
        assertThat(found.getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("A new product is unpriced — a null price, never zero")
    void newProductIsUnpriced() {
        Product saved = products.save(new Product("Gift box", Category.of("GIFTING"), Map.of()));

        Product found = products.findById(saved.getId()).orElseThrow();
        assertThat(found.getSellingPrice()).isNull();
        assertThat(found.isPriceReviewFlagged()).isFalse();
    }

    @Test
    @DisplayName("Creation and update timestamps are populated on save")
    void timestampsArePopulated() {
        Product saved = products.save(new Product("Wall clock", Category.of("DECOR"), Map.of()));

        Product found = products.findById(saved.getId()).orElseThrow();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }
}
