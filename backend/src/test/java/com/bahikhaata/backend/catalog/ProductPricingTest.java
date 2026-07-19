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
import com.bahikhaata.contracts.Money;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-product-pricing.db")
@Transactional
class ProductPricingTest {

    @Autowired
    private ProductRepository products;

    @Test
    @DisplayName("A new product is unpriced: no price set, and the absence is not zero")
    void newProductIsUnpriced() {
        Product saved = products.save(new Product("Gift box", Category.GIFTING, Map.of()));

        Product found = products.findById(saved.getId()).orElseThrow();
        assertThat(found.isPriced()).isFalse();
        assertThat(found.getSellingPrice()).isNull();
        // The distinction that matters: unpriced is not free. A null price must never be
        // read as ₹0, or an unvalued product would ring up for nothing.
        assertThat(found.getSellingPrice()).isNotEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("Unpriced products are listable as a queue to be priced")
    void unpricedProductsAreListable() {
        Product unpriced = products.save(new Product("Loose bowl", Category.KITCHEN, Map.of()));

        assertThat(products.findBySellingPriceIsNull())
                .extracting(Product::getId)
                .contains(unpriced.getId());
    }
}
