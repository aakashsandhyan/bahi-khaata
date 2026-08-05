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
import static org.mockito.Mockito.when;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.Money;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The cost-known / MRP guards of {@link ProductPricing}, and the decision-B-b relaxation that lets
 * the pricing workbench price uncosted stock while never waiving the legal MRP ceiling.
 */
@ExtendWith(MockitoExtension.class)
class ProductPricingGuardTest {

    @Mock private ProductRepository products;
    @Mock private BatchRepository batches;
    @Mock private PriceHistoryRepository priceHistory;

    private ProductPricing pricing() {
        return new ProductPricing(products, batches, priceHistory);
    }

    private Product stubProduct() {
        Product product = new Product("Cooker", Category.of("KITCHEN"), Map.of());
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        return product;
    }

    @Test
    void strictPathRefusesToPriceUncostedStock() {
        Product product = stubProduct();
        Batch uncosted = org.mockito.Mockito.mock(Batch.class);
        when(uncosted.isCosted()).thenReturn(false);
        when(batches.findByProductIdNewestFirst(product.getId())).thenReturn(List.of(uncosted));

        assertThatThrownBy(() -> pricing().setSellingPrice(product.getId(), Money.ofRupees(100)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowUncostedPricesStockWhoseLotIsNotYetCosted() {
        Product product = stubProduct();
        Batch uncosted = org.mockito.Mockito.mock(Batch.class);
        when(uncosted.getMrp()).thenReturn(null); // cost-known is skipped, so isCosted is not read
        when(batches.findByProductIdNewestFirst(product.getId())).thenReturn(List.of(uncosted));

        pricing().setSellingPrice(product.getId(), Money.ofRupees(100), true);

        assertThat(product.getSellingPrice()).isEqualTo(Money.ofRupees(100));
    }

    @Test
    void theMrpCeilingIsNeverWaivedEvenWhenUncostedIsAllowed() {
        Product product = stubProduct();
        Batch batch = org.mockito.Mockito.mock(Batch.class);
        when(batch.getMrp()).thenReturn(Money.ofRupees(90)); // cost-known skipped; MRP still checked
        when(batches.findByProductIdNewestFirst(product.getId())).thenReturn(List.of(batch));

        // Above the MRP is unlawful — the relaxation covers cost, never the legal ceiling.
        assertThatThrownBy(() -> pricing().setSellingPrice(product.getId(), Money.ofRupees(100), true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
