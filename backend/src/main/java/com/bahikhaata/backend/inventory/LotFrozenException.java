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
package com.bahikhaata.backend.inventory;

import java.util.UUID;

/**
 * A lot cannot be edited because stock from it has already been consumed.
 *
 * <p>By that point its allocated costs have been used to record cost of goods sold, so
 * changing them would rewrite margin history — the same class of error as editing the ledger.
 * A correction after this point is an adjustment movement, not an edit.
 */
public class LotFrozenException extends RuntimeException {

    private final UUID lotId;

    public LotFrozenException(UUID lotId) {
        super(
                "Lot "
                        + lotId
                        + " is frozen: stock from it has been consumed, so its costs are already "
                        + "recorded against sales. Record a correction as an adjustment instead.");
        this.lotId = lotId;
    }

    public UUID getLotId() {
        return lotId;
    }
}
