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

/** Where one carton has got to. */
public record CartonProgress(
        UUID boxId,
        String trackingNumber,
        int lines,
        long unitsExpected,
        long unitsCounted,
        long unitsUnlisted,
        boolean finished) {

    /** Nobody has been in it yet. */
    public boolean notStarted() {
        return unitsCounted == 0 && unitsUnlisted == 0 && !finished;
    }

    /** Counted in part and left — a normal state to walk away from, not an error. */
    public boolean inProgress() {
        return !finished && (unitsCounted > 0 || unitsUnlisted > 0);
    }
}
