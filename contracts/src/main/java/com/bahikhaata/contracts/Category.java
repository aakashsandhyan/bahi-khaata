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
package com.bahikhaata.contracts;

import java.util.Objects;

/**
 * A product category, identified by its code.
 *
 * <p>Was an enum, on the reasoning that a shop's departments are a fixed list. The first real
 * consignment disproved that — a third of its units fell outside the six values, with a tail
 * of single items in categories nobody had thought of — so categories are now rows in a table
 * and adding one is a data insert.
 *
 * <p>This is the code that travels on the wire and identifies the row. It is a value type
 * rather than a bare string so that a category cannot be confused with a name, a product line,
 * or any other passing text.
 *
 * @param code the category's stable identifier, such as {@code KITCHEN}
 */
public record Category(String code) {

    public Category {
        Objects.requireNonNull(code, "category code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("a category code cannot be blank");
        }
    }

    public static Category of(String code) {
        return new Category(code);
    }

    @Override
    public String toString() {
        return code;
    }
}
