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
import com.bahikhaata.contracts.Origin;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-barcode-mapping.db")
@DirtiesContext
class BarcodeMappingTest {

    @Autowired
    private ProductRepository products;

    @Autowired
    private BarcodeRepository barcodes;

    private Product newProduct(String name) {
        return products.save(new Product(name, Category.of("KITCHEN"), Map.of()));
    }

    @Test
    @DisplayName("A barcode round-trips with its product, code, and origin")
    void barcodeRoundTrips() {
        Product product = newProduct("Steel kettle");
        Barcode saved = barcodes.save(new Barcode(product, "8901234567890", Origin.MANUFACTURER));

        Barcode found = barcodes.findById(saved.getId()).orElseThrow();
        assertThat(found.getCode()).isEqualTo("8901234567890");
        assertThat(found.getOrigin()).isEqualTo(Origin.MANUFACTURER);
        assertThat(found.getProduct().getId()).isEqualTo(product.getId());
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("An internally generated code stores its INTERNAL origin")
    void internalOriginStored() {
        Product product = newProduct("Loose bin item");
        Barcode saved = barcodes.save(new Barcode(product, "INT-000001", Origin.INTERNAL));

        assertThat(barcodes.findById(saved.getId()).orElseThrow().getOrigin())
                .isEqualTo(Origin.INTERNAL);
    }

    @Test
    @DisplayName("A code already held by another product is refused")
    void duplicateCodeIsRefused() {
        Product first = newProduct("First");
        Product second = newProduct("Second");

        barcodes.save(new Barcode(first, "SHARED-CODE", Origin.MANUFACTURER));

        // The invariant: one code, at most one product. A second product claiming the same
        // code must be rejected, not silently allowed to shadow the first at checkout.
        assertThatThrownBy(
                        () -> {
                            barcodes.save(new Barcode(second, "SHARED-CODE", Origin.MANUFACTURER));
                            barcodes.flush();
                        })
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("UNIQUE constraint failed");
    }
}
