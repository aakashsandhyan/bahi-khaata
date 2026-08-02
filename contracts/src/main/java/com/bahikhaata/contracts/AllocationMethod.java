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

/**
 * How a lot's cost was spread across the products in it.
 *
 * <p>Recorded on the lot so a cost can be judged later — a figure allocated by relative
 * retail value carries different weight from one a supplier itemised — and so that changing
 * the method never silently re-costs lots already allocated.
 */
public enum AllocationMethod {
    /** Spread in proportion to each line's quantity × MRP. The normal case. */
    RELATIVE_MRP,

    /** Every line carried a known per-unit cost, so nothing was apportioned. */
    FULLY_PINNED,

    /** Costs came from a supplied per-product cost list rather than being computed. */
    IMPORTED
}
