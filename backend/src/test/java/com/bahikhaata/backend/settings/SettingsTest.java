/*
 * bahi-khaata — point of sale for Bachat Bazar
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
package com.bahikhaata.backend.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-settings.db")
class SettingsTest {

    @Autowired
    private Settings settings;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Migration seeds the defaults the application needs")
    void defaultsAreSeeded() {
        assertThat(settings.marginReviewThresholdPoints()).isEqualTo(5);
        assertThat(settings.targetMarginPercent()).isEqualTo(30);
        assertThat(settings.cartExpiry()).isEqualTo(Duration.ofMinutes(120));
    }

    @Test
    @DisplayName("Every seeded setting carries a description")
    void everySettingIsDescribed() {
        // A key-value table with no descriptions becomes unreadable the moment someone
        // other than its author opens it.
        Integer undescribed =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM setting WHERE description IS NULL OR description = ''",
                        Integer.class);

        assertThat(undescribed).isZero();
    }

    @Test
    @DisplayName("A changed value takes effect without a restart")
    void readsAreNotCached() {
        jdbc.update(
                "UPDATE setting SET setting_value = ? WHERE setting_key = ?",
                "12",
                Settings.MARGIN_REVIEW_THRESHOLD_POINTS);

        try {
            // The whole reason these live in the database rather than a properties file.
            assertThat(settings.marginReviewThresholdPoints()).isEqualTo(12);
        } finally {
            jdbc.update(
                    "UPDATE setting SET setting_value = ? WHERE setting_key = ?",
                    "5",
                    Settings.MARGIN_REVIEW_THRESHOLD_POINTS);
        }
    }

    @Test
    @DisplayName("A missing setting throws rather than falling back to a default")
    void missingSettingThrows() {
        assertThatThrownBy(() -> settings.rawValue("pricing.does_not_exist"))
                .isInstanceOf(Settings.MissingSettingException.class)
                .hasMessageContaining("pricing.does_not_exist")
                .hasMessageContaining("migration");
    }

    @Test
    @DisplayName("An unparseable setting throws rather than being silently ignored")
    void unparseableSettingThrows() {
        jdbc.update(
                "UPDATE setting SET setting_value = ? WHERE setting_key = ?",
                "not a number",
                Settings.TARGET_MARGIN_PERCENT);

        try {
            // A misconfigured install must not quietly price stock on a number nobody
            // chose; the discrepancy would surface as an unexplained margin months later.
            assertThatThrownBy(settings::targetMarginPercent)
                    .isInstanceOf(Settings.InvalidSettingException.class)
                    .hasMessageContaining("not a number")
                    .hasMessageContaining("whole number");
        } finally {
            jdbc.update(
                    "UPDATE setting SET setting_value = ? WHERE setting_key = ?",
                    "30",
                    Settings.TARGET_MARGIN_PERCENT);
        }
    }
}
