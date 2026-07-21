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
 * Whether a lot is still being counted, or has had its cost split across what arrived.
 *
 * <p>A lot opens when its manifest is imported and closes once someone has finished unpacking
 * it. Cost is apportioned at that moment and not before, because every line's share depends on
 * every other line, so no share can be final while a box remains unopened.
 *
 * <p>Closing is one-way. Costs from a closed lot may already have been used to set prices, so
 * reopening it quietly would leave those prices resting on figures that no longer exist.
 *
 * <p>Stored by name in {@code lot.state}, which carries a {@code CHECK} constraint over exactly
 * these values; a drift test holds the two in step.
 */
public enum LotState {
    /** Being unpacked. Batches from it hold stock whose cost is not yet known. */
    OPEN,

    /** Unpacking finished and the amount paid apportioned across what actually arrived. */
    CLOSED
}
