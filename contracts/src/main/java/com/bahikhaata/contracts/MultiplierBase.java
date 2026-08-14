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
package com.bahikhaata.contracts;

/** What a {@link CostBasisStrategy#MULTIPLIER} basis multiplies to derive its cost. */
public enum MultiplierBase {
    /** The lot's own entered per-unit cost — the same figure {@code FLAT_PER_UNIT} would use. */
    ENTERED_UNIT_COST,

    /** The lot's declared anchor value (MRP or ASP). */
    ANCHOR,

    /** The manifest line's stated per-unit value. */
    STATED_VALUE
}
