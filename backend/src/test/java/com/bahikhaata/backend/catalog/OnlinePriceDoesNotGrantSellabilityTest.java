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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.Marketplace;
import com.bahikhaata.contracts.Money;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * An observed online price must never stand in for an MRP.
 *
 * <p>The two are easy to confuse and were once confused here: a marketplace price is one
 * seller's asking price on one day, while MRP is the printed legal ceiling above which selling
 * is unlawful. Storing the former made a plausible-looking number available wherever the
 * latter was missing, and the temptation to let it through — the product has a price, we know
 * roughly what it is worth, why hold it off the floor — is exactly what this forbids.
 *
 * <p>These tests exist because the failure would be silent and expensive: goods on the shelf
 * priced against a number with no legal standing.
 */
class OnlinePriceDoesNotGrantSellabilityTest {

    private static final LocalDate SEEN_ON = LocalDate.of(2026, 7, 17);

    private Product product() {
        return new Product("Steel kadai 2L", Category.of("KITCHEN"), Map.of());
    }

    @Test
    void anOnlinePriceDoesNotMakeAnUnpricedProductSellable() {
        Product product = product();
        product.observeOnlinePrice(Money.ofPaise(129900), Marketplace.AMAZON, SEEN_ON);

        assertThat(product.isPriced())
                .as("an online price is not our price; it says what someone else charged")
                .isFalse();
        assertThat(product.isSellable(null)).isFalse();
    }

    @Test
    void aPricedProductWithAnOnlinePriceButNoMrpStaysOffTheFloor() {
        Product product = product();
        product.setSellingPrice(Money.ofPaise(49900));
        product.observeOnlinePrice(Money.ofPaise(129900), Marketplace.AMAZON, SEEN_ON);

        assertThat(product.isSellable(null))
                .as(
                        "knowing what Amazon charged is not knowing the printed maximum retail "
                                + "price; only an MRP read off the goods opens the gate")
                .isFalse();
    }

    @Test
    void onlyARecordedMrpOpensTheGate() {
        Product product = product();
        product.setSellingPrice(Money.ofPaise(49900));

        assertThat(product.isSellable(Money.ofPaise(99900)))
                .as("a price and an MRP, with no online price anywhere in sight")
                .isTrue();
    }

    @Test
    void aNewerObservationWinsAndAnOlderOneIsIgnored() {
        Product product = product();
        product.observeOnlinePrice(Money.ofPaise(129900), Marketplace.AMAZON, SEEN_ON);
        product.observeOnlinePrice(Money.ofPaise(99900), Marketplace.FLIPKART, SEEN_ON.plusDays(30));

        assertThat(product.getOnlinePrice()).isEqualTo(Money.ofPaise(99900));
        assertThat(product.getOnlinePriceSource()).isEqualTo(Marketplace.FLIPKART);

        // Consignments are imported when the paperwork turns up, not in the order the prices
        // were seen, so a late import of an old manifest must not undo a newer figure.
        product.observeOnlinePrice(Money.ofPaise(500000), Marketplace.AMAZON, SEEN_ON.minusDays(90));

        assertThat(product.getOnlinePrice())
                .as("an older observation must not overwrite a newer one")
                .isEqualTo(Money.ofPaise(99900));
        assertThat(product.getOnlinePriceObservedOn()).isEqualTo(SEEN_ON.plusDays(30));
    }

    @Test
    void aPriceWithoutItsDateOrMarketplaceIsRefused() {
        Product product = product();

        assertThatThrownBy(
                        () -> product.observeOnlinePrice(Money.ofPaise(129900), null, SEEN_ON))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("marketplace");

        assertThatThrownBy(
                        () ->
                                product.observeOnlinePrice(
                                        Money.ofPaise(129900), Marketplace.AMAZON, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("observation date");
    }
}
