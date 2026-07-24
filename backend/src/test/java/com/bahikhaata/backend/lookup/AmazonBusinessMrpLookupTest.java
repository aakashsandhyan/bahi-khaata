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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Amazon Business response parser, held to a plausible shape. If the real account answers with
 * different field names, this is the one place to correct — a sample response makes it exact.
 */
class AmazonBusinessMrpLookupTest {

    private final AmazonBusinessMrpLookup lookup =
            new AmazonBusinessMrpLookup(
                    "id", "secret", "refresh", "buyer@shop.in", "https://base", "https://token");

    @Test
    @DisplayName("listPrice is read as MRP; a product without one is absent, not an error")
    void parsesListPriceAsMrp() throws Exception {
        String body =
                """
                {"products": [
                  {"asin": "B0AAA", "listPrice": {"amount": 499.00, "currencyCode": "INR"}},
                  {"asin": "B0BBB", "listPrice": {"value": 1299, "currency": "INR"}},
                  {"asin": "B0CCC"}
                ]}
                """;

        Map<String, Money> found = lookup.parse(body);

        assertThat(found.get("B0AAA")).isEqualTo(Money.ofPaise(49_900));
        assertThat(found.get("B0BBB")).isEqualTo(Money.ofPaise(129_900));
        assertThat(found).as("no listing price is ordinary, not an error").doesNotContainKey("B0CCC");
    }

    @Test
    @DisplayName("Unconfigured, it declares itself unavailable rather than failing")
    void unconfiguredIsUnavailable() {
        AmazonBusinessMrpLookup bare =
                new AmazonBusinessMrpLookup("", "", "", "", "", "https://token");
        assertThat(bare.isAvailable()).isFalse();
        assertThat(bare.unavailableReason()).contains("clientId", "baseUrl");
    }
}
