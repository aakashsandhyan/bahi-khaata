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
 * Whether the shop has physically encountered a product yet.
 *
 * <p>Derived, never stored. A product enters the catalogue on paper the moment a manifest is read —
 * a name, a category, and a marketplace reference, nothing more. It becomes {@link #FOUND} the first
 * time someone lays hands on it: a code scanned onto it, or a unit counted into a batch. Until then
 * it is {@link #ON_PAPER} — a good the delivery owes that nobody has found.
 */
public enum CatalogStatus {
    /** Physically encountered — has a counted batch or a scannable code mapped onto it. */
    FOUND,

    /** Manifest-only — a marketplace reference, nothing counted, no physical code. Still unfound. */
    ON_PAPER
}
