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
import com.bahikhaata.contracts.ProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The wire shape of a product, in particular that money is integer paise and an unpriced
 * product carries a null price rather than zero. Uses the backend's own {@link ObjectMapper}
 * configuration so the assertion reflects what the endpoint actually emits, not a hand-rolled
 * mapper.
 */
class ProductResponseJsonTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("Money is an integer number of paise, not a decimal")
    void moneyIsIntegerPaise() throws Exception {
        ProductResponse priced =
                new ProductResponse("id-1", "Kettle", "KITCHEN", 20000L, null, null, false);

        ObjectNode node = (ObjectNode) json.readTree(json.writeValueAsString(priced));
        assertThat(node.get("sellingPricePaise").isIntegralNumber()).isTrue();
        assertThat(node.get("sellingPricePaise").asLong()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("An unpriced product serialises price as null, never zero")
    void unpricedSerialisesAsNull() throws Exception {
        ProductResponse unpriced =
                new ProductResponse("id-2", "Gift box", "GIFTING", null, null, null, false);

        ObjectNode node = (ObjectNode) json.readTree(json.writeValueAsString(unpriced));
        assertThat(node.hasNonNull("sellingPricePaise")).isFalse();
        // The failure this guards: a client reading a missing price as 0 and selling for free.
        assertThat(node.get("sellingPricePaise").isNull()).isTrue();
    }

    @Test
    @DisplayName("Round-trips through JSON unchanged")
    void roundTrips() throws Exception {
        ProductResponse original =
                new ProductResponse(
                        "id-3",
                        "Electric kettle",
                        "ELECTRONICS",
                        89900L,
                        "8516",
                        Map.of("warrantyMonths", 24),
                        true);

        ProductResponse back =
                json.readValue(json.writeValueAsString(original), ProductResponse.class);
        assertThat(back).isEqualTo(original);
    }
}
