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
package com.bahikhaata.backend.pricing;

import com.bahikhaata.backend.settings.Settings;
import com.bahikhaata.contracts.Category;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which target margin applies when suggesting a price.
 *
 * <p>Resolves most-specific first:
 *
 * <ol>
 *   <li>a transient custom margin, typed while pricing one product — used for that suggestion
 *       and then discarded. It is never stored, because the <em>price</em> it produces is what
 *       gets stored, and keeping the margin too would be a second source of truth for the same
 *       decision;
 *   <li>the margin configured for the product's category;
 *   <li>the global default.
 * </ol>
 *
 * <p>Read on every call rather than cached, for the same reason as {@link Settings}: these are
 * meant to be changed while the shop is trading.
 */
@Service
public class TargetMargins {

    private final JdbcTemplate jdbc;
    private final Settings settings;

    TargetMargins(JdbcTemplate jdbc, Settings settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    /** The margin configured for a category, or empty when it has none of its own. */
    @Transactional(readOnly = true)
    public Optional<Integer> forCategory(Category category) {
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject(
                            "SELECT target_margin_percent FROM category_margin WHERE category = ?",
                            Integer.class,
                            category.code()));
        } catch (EmptyResultDataAccessException noRow) {
            return Optional.empty();
        }
    }

    /** The global fallback, used where a category has no margin of its own. */
    @Transactional(readOnly = true)
    public int globalDefault() {
        return settings.targetMarginPercent();
    }

    /**
     * The margin to use for a suggestion.
     *
     * @param category the product's category
     * @param customMarginPercent a margin supplied for this one suggestion, or null
     */
    @Transactional(readOnly = true)
    public int resolve(Category category, Integer customMarginPercent) {
        if (customMarginPercent != null) {
            return customMarginPercent;
        }
        return forCategory(category).orElseGet(this::globalDefault);
    }
}
