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
package com.bahikhaata.backend.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import com.bahikhaata.contracts.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reading a listed price out of Keepa's answer.
 *
 * <p>Parsing is tested apart from the network so it needs no key and no internet. That matters
 * twice over: a credential must never be required to run the build, and the shape of this
 * response is the thing most likely to be wrong, since it was written from documentation rather
 * than from a live call.
 */
class KeepaMrpLookupTest {

    private final KeepaMrpLookup lookup = new KeepaMrpLookup("");

    @Test
    @DisplayName("The most recent listed price is taken from the history")
    void takesTheLatestPrice() {
        // Keepa histories alternate timestamp and price, oldest first, in whole rupees.
        String body = """
                {"products":[{"asin":"B07KT9Q54M","csv":[[],[],[],[],
                  [7000000, 349, 7100000, 299, 7200000, 399]]}]}
                """;

        assertThat(lookup.parse(body))
                .containsEntry("B07KT9Q54M", Money.ofPaise(39_900));
    }

    @Test
    @DisplayName("Gaps in the history are skipped rather than read as a price")
    void skipsGaps() {
        // -1 is Keepa's way of saying it had no data at that moment. Reading it as a price
        // would put a negative MRP on the goods.
        String body = """
                {"products":[{"asin":"B07KT9Q54M","csv":[[],[],[],[],
                  [7000000, 349, 7100000, -1]]}]}
                """;

        assertThat(lookup.parse(body))
                .as("the last real figure stands; an absence is not a price change")
                .containsEntry("B07KT9Q54M", Money.ofPaise(34_900));
    }

    @Test
    @DisplayName("A product with no listed price at all is simply absent")
    void noListedPrice() {
        String body = """
                {"products":[{"asin":"B07KT9Q54M","csv":[[],[],[],[],[]]}]}
                """;

        assertThat(lookup.parse(body))
                .as("plenty of goods have no listing left, which is ordinary and not an error")
                .isEmpty();
    }

    @Test
    @DisplayName("Several products come back together")
    void severalProducts() {
        String body = """
                {"products":[
                  {"asin":"AAA","csv":[[],[],[],[],[1, 100]]},
                  {"asin":"BBB","csv":[[],[],[],[],[1, 250]]}]}
                """;

        assertThat(lookup.parse(body))
                .containsEntry("AAA", Money.ofPaise(10_000))
                .containsEntry("BBB", Money.ofPaise(25_000));
    }

    @Test
    @DisplayName("Without a key it reports itself unavailable rather than failing")
    void unconfiguredIsANormalState() {
        assertThat(lookup.isAvailable()).isFalse();
        assertThat(lookup.unavailableReason())
                .as("a shop with no key must still be able to open cartons")
                .contains("Unpacking is unaffected");
        assertThat(lookup.lookup(java.util.List.of("AAA"))).isEmpty();
    }

    @Test
    @DisplayName("The key never appears in what it says about itself")
    void theKeyIsNeverDisclosed() {
        KeepaMrpLookup configured = new KeepaMrpLookup("secret-key-value");

        assertThat(configured.isAvailable()).isTrue();
        assertThat(configured.unavailableReason()).doesNotContain("secret-key-value");
        assertThat(configured.toString()).doesNotContain("secret-key-value");
    }
}
