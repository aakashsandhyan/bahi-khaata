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

import java.util.UUID;

/**
 * How far one delivery has been unpacked.
 *
 * <p>Counted in cartons and items rather than percentages, because that is what someone standing
 * in front of a pallet can check against what they can see.
 *
 * @param unitsUnlisted things found that the supplier's list never mentioned
 * @param itemsWithoutMrp items counted whose printed price nobody has entered — they cannot be
 *     sold until someone does, so this is the queue that matters after the cartons are empty
 */
public record DeliveryProgress(
        UUID lotId,
        String supplier,
        String category,
        int cartonsTotal,
        int cartonsFinished,
        int cartonsStarted,
        long unitsExpected,
        long unitsCounted,
        long unitsUnlisted,
        long itemsWithoutMrp,
        boolean closed) {

    public int cartonsNotStarted() {
        return cartonsTotal - cartonsFinished - cartonsStarted;
    }
}
