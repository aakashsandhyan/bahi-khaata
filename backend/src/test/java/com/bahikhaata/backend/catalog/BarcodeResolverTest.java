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
package com.bahikhaata.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.Origin;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-barcode-resolver.db")
@DirtiesContext
class BarcodeResolverTest {

    @Autowired
    private ProductRepository products;

    @Autowired
    private BarcodeRepository barcodes;

    @Autowired
    private BarcodeResolver resolver;

    @Test
    @DisplayName("A known code resolves to its product, usable outside the session")
    void knownCodeResolves() {
        Product product = products.save(new Product("Steel kettle", Category.of("KITCHEN"), Map.of()));
        barcodes.save(new Barcode(product, "8901234567890", Origin.MANUFACTURER));

        var resolved = resolver.resolve("8901234567890");

        assertThat(resolved).isPresent();
        // Touch a field to prove it is a real product, not a lazy proxy that would throw
        // once the session has closed.
        assertThat(resolved.orElseThrow().getName()).isEqualTo("Steel kettle");
        assertThat(resolved.orElseThrow().getId()).isEqualTo(product.getId());
    }

    @Test
    @DisplayName("An unrecognised code resolves to empty and writes nothing")
    void unknownCodeResolvesEmptyAndCreatesNothing() {
        products.save(new Product("Steel kettle", Category.of("KITCHEN"), Map.of()));

        long productsBefore = products.count();
        long barcodesBefore = barcodes.count();

        assertThat(resolver.resolve("NO-SUCH-CODE")).isEmpty();

        // The guarantee that matters: a scanner producing an unknown code must not conjure
        // a product or a barcode row.
        assertThat(products.count()).isEqualTo(productsBefore);
        assertThat(barcodes.count()).isEqualTo(barcodesBefore);
    }
}
