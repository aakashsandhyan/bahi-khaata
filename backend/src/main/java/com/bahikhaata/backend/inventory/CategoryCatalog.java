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
package com.bahikhaata.backend.inventory;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The shop's whole category list. Categories are data in the {@code category} table (they stopped
 * being an enum once a real consignment overran the fixed six), read here by code for the pickers
 * that need every category rather than only the ones a given lot happens to hold.
 */
@Component
public class CategoryCatalog {

    private final JdbcTemplate jdbc;

    CategoryCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every category code, alphabetical — the fallback choices when a lot names none of its own. */
    public List<String> allCodes() {
        return jdbc.queryForList("SELECT code FROM category ORDER BY code", String.class);
    }
}
