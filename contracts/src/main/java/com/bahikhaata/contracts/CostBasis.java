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
 * How one batch's unit cost was arrived at.
 *
 * <p>The lot records the method; this records what actually happened to this line, which can
 * differ — a single line may be pinned inside an otherwise apportioned lot. Unreconstructable
 * after the fact, which is why it is captured at the time.
 */
public enum CostBasis {
    /** Apportioned from the lot amount. */
    ALLOCATED,

    /** Taken from a known per-unit cost, and excluded from the apportioned pool. */
    PINNED,

    /** Taken from a supplied cost list. */
    IMPORTED
}
