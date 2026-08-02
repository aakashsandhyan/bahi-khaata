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
package com.bahikhaata.backend.inventory.allocation;

import com.bahikhaata.contracts.AllocationMethod;

/**
 * How much of the apportioned remainder a line should attract, relative to the others.
 *
 * <p>This is the whole of what varies between allocation strategies. Everything else —
 * freight, removing pinned lines from the pool, exact reconciliation in paise, dividing a
 * share into a unit cost — is common, lives in {@link CostAllocator}, and is tested once.
 * Adding a strategy means answering one question: what is this line worth, relative to its
 * neighbours?
 *
 * <p>Weights are unitless and only their ratios matter. They are {@code long} rather than a
 * fraction so the arithmetic stays exact; a strategy that would naturally produce fractions
 * should scale them up instead.
 */
public interface AllocationWeighting {

    /** The method this weighting represents, recorded on the lot. */
    AllocationMethod method();

    /**
     * This line's relative weight. Must be positive: a zero-weight line would be allocated
     * nothing, making its stock free, and a negative one is meaningless.
     */
    long weightOf(AllocationLine line);
}
