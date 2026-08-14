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
package com.bahikhaata.backend.tax;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-gst-rates.db")
@Transactional
class GstRatesTest {

    @Autowired private GstRates gstRates;
    @Autowired private GstRateService rateService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("An unclassified product falls back to the 18% default")
    void unclassifiedUsesDefault() {
        assertThat(gstRates.resolveBasisPoints(null)).isEqualTo(1800);
        assertThat(gstRates.resolveBasisPoints("")).isEqualTo(1800);
    }

    @Test
    @DisplayName("A sub-category's seeded rate is resolved — metal utensils at 5%")
    void resolvesSeededRate() {
        assertThat(gstRates.resolveBasisPoints("KITCHEN_METAL")).isEqualTo(500);
        assertThat(gstRates.resolveBasisPoints("WIRELESS_GENERAL")).isEqualTo(1800);
    }

    @Test
    @DisplayName("Setting a rate closes the old and opens the new — exactly one active row")
    void setRateClosesAndOpens() {
        rateService.setRate("TOYS_GENERAL", 1200); // move toys from 5% to a hypothetical 12%

        assertThat(gstRates.resolveBasisPoints("TOYS_GENERAL")).isEqualTo(1200);
        Integer active =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM gst_rate WHERE sub_category = 'TOYS_GENERAL' AND is_active = 1",
                        Integer.class);
        assertThat(active).isEqualTo(1);
        Integer history =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM gst_rate WHERE sub_category = 'TOYS_GENERAL'", Integer.class);
        assertThat(history).as("the old rate is kept as closed history").isEqualTo(2);
    }

    @Test
    @DisplayName("A rate cannot be set for an unknown sub-category")
    void rejectsUnknownSubCategory() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> rateService.setRate("NOPE", 500))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
