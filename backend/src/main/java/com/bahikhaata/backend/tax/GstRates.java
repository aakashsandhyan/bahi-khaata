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

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The GST rate (basis points) that applies to a product.
 *
 * <p>Resolves the sub-category's single active rate, falling back to the global default when the
 * product carries no sub-category. Read on every call rather than cached, like {@code TargetMargins}
 * — rates are meant to be edited while the shop trades, and the resolved rate is snapshotted onto
 * the sale anyway, so a change never disturbs a past bill.
 */
@Service
public class GstRates {

    private final JdbcTemplate jdbc;

    GstRates(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The rate in basis points for a product's sub-category, or the global default if none. */
    @Transactional(readOnly = true)
    public int resolveBasisPoints(String subCategory) {
        if (subCategory != null && !subCategory.isBlank()) {
            try {
                return jdbc.queryForObject(
                        "SELECT gst_basis_points FROM gst_rate WHERE sub_category = ? AND is_active = 1",
                        Integer.class,
                        subCategory);
            } catch (EmptyResultDataAccessException noActiveRate) {
                // A sub-category without an active rate falls back to the default, not an error.
            }
        }
        return globalDefaultBasisPoints();
    }

    /** Every active rate, for pickers: sub-category code and its basis points. */
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> activeOptions() {
        return jdbc.queryForList(
                "SELECT sub_category, gst_basis_points FROM gst_rate WHERE is_active = 1 "
                        + "ORDER BY sub_category");
    }

    /** The fallback rate for an unclassified product — the `gst.default_basis_points` setting (18%). */
    @Transactional(readOnly = true)
    public int globalDefaultBasisPoints() {
        String raw =
                jdbc.queryForObject(
                        "SELECT setting_value FROM setting WHERE setting_key = 'gst.default_basis_points'",
                        String.class);
        return Integer.parseInt(raw.trim());
    }
}
