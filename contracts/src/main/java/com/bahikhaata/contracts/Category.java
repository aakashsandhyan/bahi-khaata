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

/**
 * The governed set of product categories.
 *
 * <p>A fixed set, not free text: an unconstrained category would let "Electronics",
 * "electronics", and a tired-evening "Electronis" become three categories, and
 * per-category configuration such as target margin would then attach to inconsistent
 * keys. The department-level spread here is stable (report §1), so it is an enum; if
 * categories ever churn, this becomes a lookup table.
 *
 * <p>Stored by {@link Enum#name()} in the {@code product.category} column, which carries a
 * {@code CHECK} constraint over exactly these names. A test asserts the two sets are equal
 * so a value added to one but not the other fails the build rather than production.
 *
 * <p>Lives in {@code contracts} because it is a wire type: the terminal shows a category
 * and goods-in picks one, so backend, terminal, and dashboard share a single definition.
 */
public enum Category {
    HOME_ESSENTIALS,
    KITCHEN,
    ELECTRONICS,
    GIFTING,
    DECOR,
    FASHION
}
