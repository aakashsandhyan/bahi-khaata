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
 * Why stock moved.
 *
 * <p>Direction is carried by the sign of the quantity, not by this — so the type records the
 * reason, which is what reconciling a discrepancy actually needs. "Three fewer kettles" is
 * not useful; "three sold" versus "three written off" is.
 */
public enum MovementType {
    /** Stock arriving from a supplier. Always positive. */
    PURCHASE_RECEIPT,

    /** Stock leaving through a sale. Always negative, and carries cost of goods sold. */
    SALE,

    /** Stock leaving because it cannot be sold — damage found after receipt. Negative. */
    WRITE_OFF,

    /** A correction after a stock take. May go either way, which is why direction is a sign. */
    ADJUSTMENT
}
