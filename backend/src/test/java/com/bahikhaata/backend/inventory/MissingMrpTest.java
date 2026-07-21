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
package com.bahikhaata.backend.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.Money;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock can arrive without a printed maximum retail price, and is received and counted just
 * the same — but it cannot be sold until someone has read one off the goods.
 *
 * <p>MRP is a legal figure rather than a convenience: selling above it is unlawful, and the
 * label a customer reads shows the saving against it. A manifest's selling price is not the
 * same thing and must never be substituted for it.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-missing-mrp.db")
@Transactional
class MissingMrpTest {

    @Autowired
    private ProductRepository products;

    @Autowired
    private LotRepository lots;

    @Autowired
    private BatchRepository batches;

    private Batch receiveWithoutMrp(String name) {
        Product product = products.save(new Product(name, Category.of("FOOTWEAR"), Map.of()));
        Lot lot =
                lots.save(
                        new Lot(
                                "Sushil",
                                LocalDate.of(2026, 7, 17),
                                Money.ofRupees(97_680),
                                Money.ZERO,
                                AllocationMethod.FULLY_PINNED));
        return batches.save(
                new Batch(
                        product, lot, Money.ofPaise(149_200), CostBasis.PINNED,
                        5, 0, null, false));
    }

    @Test
    @DisplayName("A batch can be received with no MRP recorded")
    void receivedWithoutMrp() {
        Batch batch = receiveWithoutMrp("Nike Court Vision");

        // A cost-based manifest states what the goods cost and never what they retail for,
        // so this is the normal state for such a delivery until it is unpacked.
        assertThat(batches.findById(batch.getId()).orElseThrow().getMrp()).isNull();
        assertThat(batch.getQuantityReceived()).isEqualTo(5);
    }

    @Test
    @DisplayName("A product with no MRP is not sellable, even once priced")
    void noMrpMeansNotSellable() {
        Batch batch = receiveWithoutMrp("Nike Pegasus");
        Product product = batch.getProduct();

        product.setSellingPrice(Money.ofRupees(2_499));

        assertThat(product.isPriced()).isTrue();
        // Priced is necessary but not sufficient: without a printed maximum there is no
        // lawful ceiling to price beneath and nothing to show a saving from.
        assertThat(product.isSellable(batch.getMrp())).isFalse();
    }

    @Test
    @DisplayName("Recording the MRP makes a priced product sellable")
    void recordingMrpUnlocksIt() {
        Batch batch = receiveWithoutMrp("ASICS Gel-DS");
        Product product = batch.getProduct();
        product.setSellingPrice(Money.ofRupees(2_499));

        batch.recordMrp(Money.ofRupees(5_999), false);

        assertThat(product.isSellable(batch.getMrp())).isTrue();
        assertThat(batch.getMrp()).isEqualTo(Money.ofRupees(5_999));
        assertThat(batch.isMrpEstimate()).isFalse();
    }

    @Test
    @DisplayName("An MRP found by lookup rather than read off the goods is marked an estimate")
    void lookedUpMrpIsAnEstimate() {
        Batch batch = receiveWithoutMrp("boAt Stone 1200");

        batch.recordMrp(Money.ofRupees(3_999), true);

        // So a figure someone found online is never mistaken for one printed on the pack.
        assertThat(batch.isMrpEstimate()).isTrue();
    }

    @Test
    @DisplayName("An MRP must be a real, positive amount")
    void mrpMustBePositive() {
        Batch batch = receiveWithoutMrp("Guard rails");

        assertThatThrownBy(() -> batch.recordMrp(Money.ZERO, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> batch.recordMrp(null, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("An unpriced product is not sellable however well known its MRP")
    void mrpAloneIsNotEnough() {
        Batch batch = receiveWithoutMrp("Unpriced but tagged");
        batch.recordMrp(Money.ofRupees(1_999), false);

        assertThat(batch.getProduct().isSellable(batch.getMrp())).isFalse();
    }
}
