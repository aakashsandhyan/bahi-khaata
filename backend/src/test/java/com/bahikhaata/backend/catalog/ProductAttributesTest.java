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
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The JSON attribute column carries whatever a category needs without a schema change. This
 * is the flexibility the hybrid model exists for: standard columns for what every product
 * shares, one JSON document for the parts that vary — serial and warranty for electronics,
 * size and colour for fashion, nothing for a plain kitchen item.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-product-attributes.db")
@DirtiesContext
class ProductAttributesTest {

    @Autowired
    private ProductRepository products;

    @Test
    @DisplayName("Category-specific attributes round-trip unchanged")
    void attributesRoundTrip() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("serialNumber", "SN-4471");
        attributes.put("warrantyMonths", 24);
        attributes.put("inBox", true);

        var saved = products.save(new Product("Electric kettle", Category.ELECTRONICS, attributes));

        Map<String, Object> found = products.findById(saved.getId()).orElseThrow().getAttributes();
        assertThat(found)
                .containsEntry("serialNumber", "SN-4471")
                .containsEntry("warrantyMonths", 24)
                .containsEntry("inBox", true);
    }

    @Test
    @DisplayName("A product with no attributes is valid")
    void noAttributesIsValid() {
        var withEmpty = products.save(new Product("Steel bowl", Category.KITCHEN, Map.of()));
        var withNull = products.save(new Product("Steel plate", Category.KITCHEN, null));

        assertThat(products.findById(withEmpty.getId())).isPresent();
        assertThat(products.findById(withNull.getId())).isPresent();
        assertThat(products.findById(withNull.getId()).orElseThrow().getAttributes()).isNull();
    }

    @Test
    @DisplayName("Attribute names never used before need no migration")
    void unfamiliarAttributeNamesNeedNoMigration() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("fabricGsm", 180);
        attributes.put("neverSeenBefore", "arbitrary");

        // No schema change was made for these names; if the JSON column did not carry
        // arbitrary keys this save would fail.
        var saved = products.save(new Product("Cotton throw", Category.FASHION, attributes));

        Map<String, Object> found = products.findById(saved.getId()).orElseThrow().getAttributes();
        assertThat(found)
                .containsEntry("fabricGsm", 180)
                .containsEntry("neverSeenBefore", "arbitrary");
    }
}
