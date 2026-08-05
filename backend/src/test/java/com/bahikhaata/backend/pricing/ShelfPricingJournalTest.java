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
package com.bahikhaata.backend.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.backend.inventory.Lot;
import com.bahikhaata.backend.inventory.LotRepository;
import com.bahikhaata.backend.shelf.PriceHistory;
import com.bahikhaata.backend.shelf.PriceHistoryRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.PriceExistingRequest;
import com.bahikhaata.contracts.PriceManualRequest;
import com.bahikhaata.contracts.ShelfPricedProduct;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ShelfPricing}'s two save paths ({@link ShelfPricing#saveExisting} and {@link
 * ShelfPricing#saveManual}) each end at {@code ProductPricing.setSellingPrice} — the single
 * choke point that journals every price change (design decision D5 of palletworks-inventory).
 * {@link ShelfPricingTest} mocks that choke point away to test ShelfPricing's own logic in
 * isolation; this class wires the real thing, against a real database, to prove the journal row
 * actually appears at the end of each path.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-shelf-pricing-journal.db")
@Transactional
class ShelfPricingJournalTest {

    @Autowired private ShelfPricing shelfPricing;
    @Autowired private ProductRepository products;
    @Autowired private LotRepository lots;
    @Autowired private BatchRepository batches;
    @Autowired private PriceHistoryRepository priceHistory;

    private Lot openLot(String supplier) {
        return lots.save(
                new Lot(
                        supplier,
                        LocalDate.of(2026, 8, 1),
                        Money.ofRupees(50_000),
                        Money.ZERO,
                        AllocationMethod.RELATIVE_MRP));
    }

    @Test
    @DisplayName("saveExisting journals its price set via the choke point")
    void saveExistingJournals() {
        Product product = products.save(new Product("Journal Cooker", Category.of("KITCHEN"), Map.of()));
        Lot lot = openLot("Journal Supplier A");
        Batch batch =
                batches.save(
                        new Batch(
                                product, lot, Money.ofPaise(10_000), CostBasis.ALLOCATED, 5, 0,
                                Money.ofRupees(300), false));

        shelfPricing.saveExisting(
                new PriceExistingRequest(
                        product.getId(), batch.getId(), "KITCHEN", 19_900L, null, null, null, false,
                        null, null));

        List<PriceHistory> rows = priceHistory.findByProductIdOrderByCreatedAtDesc(product.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOldPrice()).as("first-ever set").isNull();
        assertThat(rows.get(0).getNewPrice()).isEqualTo(Money.ofPaise(19_900L));
    }

    @Test
    @DisplayName("saveManual journals its price set via the choke point")
    void saveManualJournals() {
        Lot lot = openLot("Journal Supplier B");

        ShelfPricedProduct saved =
                shelfPricing.saveManual(
                        new PriceManualRequest(
                                lot.getId(), "Hand-keyed Journal Item", "KITCHEN", "GOOD", 3L, 24_900L,
                                29_900L, null, null));

        List<PriceHistory> rows =
                priceHistory.findByProductIdOrderByCreatedAtDesc(saved.productId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOldPrice()).as("first-ever set").isNull();
        assertThat(rows.get(0).getNewPrice()).isEqualTo(Money.ofPaise(24_900L));
    }
}
