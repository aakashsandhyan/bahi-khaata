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
package com.bahikhaata.backend.settings;

import java.time.Duration;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Typed access to the business parameters in the {@code setting} table.
 *
 * <p>Read on every call rather than cached. These values are meant to be changed while the
 * backend is running — a cached threshold that only takes effect after a restart defeats
 * the reason for putting them in the database at all. At one terminal and a handful of
 * reads per sale, the cost is not worth reasoning about.
 *
 * <p>A missing or unparseable setting throws. Falling back to a hard-coded default would
 * mean a misconfigured install quietly pricing stock on numbers nobody chose, and the
 * discrepancy would surface as an unexplained margin months later.
 */
@Component
public class Settings {

    public static final String MARGIN_REVIEW_THRESHOLD_POINTS =
            "pricing.margin_review_threshold_points";
    public static final String TARGET_MARGIN_PERCENT = "pricing.target_margin_percent";
    public static final String CART_EXPIRY_MINUTES = "checkout.cart_expiry_minutes";

    private final JdbcTemplate jdbc;

    Settings(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Percentage points of gross margin loss that flags a product for price review.
     *
     * <p>Points, not percent: a margin falling from 30% to 25% has lost five points. The
     * distinction matters because the same movement expressed as a percentage change is
     * about seventeen percent, and confusing the two makes the threshold fire at roughly
     * three times the intended sensitivity.
     */
    public int marginReviewThresholdPoints() {
        return readInt(MARGIN_REVIEW_THRESHOLD_POINTS);
    }

    /** Gross margin used to suggest a price for an unpriced product. */
    public int targetMarginPercent() {
        return readInt(TARGET_MARGIN_PERCENT);
    }

    /** Inactivity after which an abandoned cart is discarded. */
    public Duration cartExpiry() {
        return Duration.ofMinutes(readInt(CART_EXPIRY_MINUTES));
    }

    /** Raw access, for a settings screen that lists everything without knowing the keys. */
    public String rawValue(String key) {
        try {
            return jdbc.queryForObject(
                    "SELECT setting_value FROM setting WHERE setting_key = ?", String.class, key);
        } catch (EmptyResultDataAccessException absent) {
            throw new MissingSettingException(key);
        }
    }

    private int readInt(String key) {
        String raw = rawValue(key);
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException notANumber) {
            throw new InvalidSettingException(key, raw, "a whole number");
        }
    }

    /** A setting the application needs is absent from the database. */
    public static class MissingSettingException extends RuntimeException {
        public MissingSettingException(String key) {
            super("Setting \"" + key + "\" is not present. It should have been seeded by a "
                    + "migration — check that migrations have been applied.");
        }
    }

    /** A setting is present but cannot be read as the type the application expects. */
    public static class InvalidSettingException extends RuntimeException {
        public InvalidSettingException(String key, String value, String expected) {
            super("Setting \"" + key + "\" is \"" + value + "\", which is not " + expected + ".");
        }
    }
}
