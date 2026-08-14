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
 * How a lot declaring a cost basis derives each product's per-unit cost.
 *
 * <p>Lives on the lot, not the batch: the batch only ever holds the resulting pinned cost
 * ({@link CostBasis#PINNED}). A lot that declares none of these keeps its existing behaviour —
 * the manifest rate, or the amount-paid apportionment — untouched.
 */
public enum CostBasisStrategy {
    /** Every unit in the lot costs the same stated per-unit figure. */
    FLAT_PER_UNIT,

    /** Cost is a fixed percentage of an anchor value (MRP or ASP), in basis points. */
    PERCENT_OF_ANCHOR,

    /** Cost is banded by the item's MRP: a per-lot rate card of MRP ranges to a cost each. */
    MRP_RATE_RANGE,

    /** Cost is a multiplier on a chosen base — an entered cost, an anchor, or the stated value. */
    MULTIPLIER
}
