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
 * Weights a line by the retail value of what arrived: quantity received × MRP.
 *
 * <p>The standard method for a joint purchase, and the one that makes per-lot margin mean
 * anything. A kettle and a keychain off the same pallet did not cost the same, and weighting
 * by what they retail for is the closest available proxy for what each was worth in the deal.
 *
 * <p>Uses quantity <em>received</em>, damaged units included: the supplier was paid for those
 * too, so they count toward what the line was worth in the purchase. Their cost is then
 * absorbed by the sellable units when the share is divided.
 *
 * <p>Considered and rejected: an equal split per unit. It is simpler, and defensible only
 * when a pallet is uniform. On a mixed one it gives a ₹50 keychain the same cost as an ₹800
 * kettle — so the keychain reports a catastrophic loss on every sale and the kettle an
 * invented profit, destroying exactly the per-lot margin visibility FIFO was chosen for.
 */
public final class RelativeMrpWeighting implements AllocationWeighting {

    @Override
    public AllocationMethod method() {
        return AllocationMethod.RELATIVE_MRP;
    }

    @Override
    public long weightOf(AllocationLine line) {
        return Math.multiplyExact(line.quantityReceived(), line.weighingValue().paise());
    }
}
