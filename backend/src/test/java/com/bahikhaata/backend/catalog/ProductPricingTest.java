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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        Product saved = products.save(new Product("Gift box", Category.of("GIFTING"), Map.of()));

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
        Product unpriced = products.save(new Product("Loose bowl", Category.of("KITCHEN"), Map.of()));

        assertThat(products.findBySellingPriceIsNull())
                .extracting(Product::getId)
                .contains(unpriced.getId());
    }

    @Test
    @DisplayName("Setting a price makes the product priced and persists")
    void settingAPricePersists() {
        Product product = products.save(new Product("Steel bottle", Category.of("KITCHEN"), Map.of()));

        product.setSellingPrice(Money.ofRupees(200));
        products.save(product);

        Product found = products.findById(product.getId()).orElseThrow();
        assertThat(found.isPriced()).isTrue();
        assertThat(found.getSellingPrice()).isEqualTo(Money.ofRupees(200));
    }

    @Test
    @DisplayName("A price must be a real, positive amount")
    void priceMustBePositive() {
        Product product = new Product("Kettle", Category.of("KITCHEN"), Map.of());

        // Cannot un-price by passing null.
        assertThatThrownBy(() -> product.setSellingPrice(null))
                .isInstanceOf(NullPointerException.class);
        // Cannot set zero or negative — a giveaway or a negative price is not a price.
        assertThatThrownBy(() -> product.setSellingPrice(Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product.setSellingPrice(Money.ofPaise(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A set price is unchanged by a persistence round trip")
    void priceSurvivesReload() {
        // The narrow invariant testable in section 2: nothing incidental — a re-save, a
        // reload — alters a price once set. The larger invariant, that receiving stock at a
        // new cost never changes the price, is exercised in section 5 where batches exist.
        Product product = products.save(new Product("Wall clock", Category.of("DECOR"), Map.of()));
        product.setSellingPrice(Money.ofRupees(150));
        products.save(product);

        Product reloaded = products.findById(product.getId()).orElseThrow();
        products.save(reloaded); // a no-op re-save must not disturb the price

        assertThat(products.findById(product.getId()).orElseThrow().getSellingPrice())
                .isEqualTo(Money.ofRupees(150));
    }
}
