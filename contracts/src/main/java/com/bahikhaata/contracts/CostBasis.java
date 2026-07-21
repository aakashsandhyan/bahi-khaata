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
    IMPORTED,

    /**
     * Weighed at the lot's average unit value, because the goods arrived without appearing on
     * the manifest and so carry no stated value of their own.
     *
     * <p>A genuine estimate: right on average and wrong on any particular item, since a
     * surplus carton of something dear comes out undercosted and something cheap overcosted.
     * Recorded as its own basis rather than passed off as {@link #ALLOCATED} so a margin
     * resting on it can be recognised for what it is.
     */
    ESTIMATED,

    /**
     * Nothing, because the goods arrived unusable and their share went to the ones that can be
     * sold.
     *
     * <p>Recorded rather than left as a bare zero, which reads like a mistake. A cost of nothing
     * is a real answer here and deserves to say why.
     */
    ABSORBED
}
