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
package com.bahikhaata.backend.inventory.allocation;

import com.bahikhaata.contracts.Money;

/**
 * One line of a delivery, as the allocator sees it.
 *
 * <p>Deliberately not an entity: the allocator takes plain values and returns plain values, so
 * the arithmetic can be read and tested without persistence in the way. The {@code reference}
 * is whatever key the caller wants back — a product id in practice.
 *
 * @param reference the caller's key, returned untouched
 * @param quantityReceived how many arrived, damaged included — this is what was paid for
 * @param quantityDamaged how many of those cannot be sold
 * @param mrp printed retail price, or an estimate where none is printed
 * @param pinnedUnitCost a known per-unit cost, or null when the line is to be apportioned
 */
public record AllocationLine(
        String reference,
        long quantityReceived,
        long quantityDamaged,
        Money mrp,
        Money pinnedUnitCost) {

    public AllocationLine {
        if (quantityReceived <= 0) {
            throw new IllegalArgumentException(
                    "line " + reference + ": quantity received must be positive");
        }
        if (quantityDamaged < 0 || quantityDamaged > quantityReceived) {
            throw new IllegalArgumentException(
                    "line " + reference + ": damaged must be between zero and received");
        }
        if (mrp == null || !mrp.isPositive()) {
            throw new IllegalArgumentException("line " + reference + ": MRP must be positive");
        }
        if (pinnedUnitCost != null && pinnedUnitCost.isNegative()) {
            throw new IllegalArgumentException(
                    "line " + reference + ": pinned cost cannot be negative");
        }
    }

    /** Units that can actually be sold, and therefore the divisor for unit cost. */
    public long sellableQuantity() {
        return quantityReceived - quantityDamaged;
    }

    public boolean isPinned() {
        return pinnedUnitCost != null;
    }

    /**
     * What a pinned line costs in total: the pinned rate times everything that arrived,
     * damaged included, because that is what was actually paid for it.
     */
    public Money pinnedTotal() {
        return pinnedUnitCost.times(quantityReceived);
    }
}
