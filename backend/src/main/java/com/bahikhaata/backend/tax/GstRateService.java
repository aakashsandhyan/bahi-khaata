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

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Editing GST rates and the sub-category vocabulary — the admin side of {@link GstRates}.
 *
 * <p>A rate change is <strong>close-and-open</strong>: the current active rate is stamped closed and
 * a new active rate opened, so history is preserved and there is always exactly one live rate per
 * sub-category (the partial unique index {@code idx_gst_rate_active} guarantees it even under a
 * concurrent bug). Rates are basis points (18% = 1800) so odd slabs stay exact.
 */
@Service
public class GstRateService {

    private static final DateTimeFormatter ISO_MILLIS =
            new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    private final JdbcTemplate jdbc;

    GstRateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every sub-category and its current active rate in basis points, for the admin screen. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> rates() {
        return jdbc.queryForList(
                "SELECT s.code, s.name, s.category, r.gst_basis_points"
                        + " FROM sub_category s"
                        + " LEFT JOIN gst_rate r ON r.sub_category = s.code AND r.is_active = 1"
                        + " ORDER BY s.category, s.code");
    }

    /**
     * Sets the active rate for a sub-category, closing the previous one. Idempotent-safe: setting the
     * same value still records a new active row so the effective date reflects the edit.
     */
    @Transactional
    public void setRate(String subCategory, int basisPoints) {
        if (basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("gst basis points must be between 0 and 10000");
        }
        Integer exists =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sub_category WHERE code = ?", Integer.class, subCategory);
        if (exists == null || exists == 0) {
            throw new IllegalArgumentException("no such sub-category: " + subCategory);
        }
        String today = LocalDate.now().toString();
        String now = ISO_MILLIS.format(Instant.now());
        jdbc.update(
                "UPDATE gst_rate SET is_active = 0, effective_to = ?, updated_at = ?"
                        + " WHERE sub_category = ? AND is_active = 1",
                today, now, subCategory);
        jdbc.update(
                "INSERT INTO gst_rate (id, sub_category, gst_basis_points, effective_from, effective_to,"
                        + " is_active, created_at, updated_at) VALUES (?, ?, ?, ?, NULL, 1, ?, ?)",
                UUID.randomUUID().toString(), subCategory, basisPoints, today, now, now);
    }
}
