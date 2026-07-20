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

import com.bahikhaata.contracts.AllocationMethod;

/**
 * A second strategy, existing to prove the seam rather than to be used.
 *
 * <p>Splits the lot amount evenly across every unit, ignoring what each is worth. Defensible
 * only for a genuinely uniform pallet, and wrong for a mixed one — which is exactly why
 * {@link RelativeMrpWeighting} is what production uses. It lives in test sources so no
 * speculative strategy ships; if equal-split is ever wanted for real, it moves.
 *
 * <p>What it demonstrates is how small a strategy is: one method, returning a relative weight.
 * It cannot affect freight, pinning, reconciliation or unit cost, because none of those are
 * its business — they live in {@link CostAllocator} and are tested once.
 */
public final class EqualPerUnitWeighting implements AllocationWeighting {

    @Override
    public AllocationMethod method() {
        // No enum value of its own: this is a stand-in, and adding one to the governed set
        // for a strategy that does not ship would be dishonest.
        return AllocationMethod.IMPORTED;
    }

    @Override
    public long weightOf(AllocationLine line) {
        return line.quantityReceived();
    }
}
