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

import java.util.List;

/**
 * Correcting a lot's data-entry fields — every field is a partial update, not a replacement.
 *
 * <p>{@code null} on any field means "leave it as it is"; there is no other way to send "no
 * change" over JSON for a field that also has a legitimate empty value. {@code categoryCode} is
 * the one exception: {@code null} leaves the lot's category alone, but {@code ""} explicitly
 * clears it back to "no default", since a lot losing its category is a real, distinct choice
 * from simply not mentioning it.
 *
 * <p>The cost-basis fields are the other exception, and travel together rather than one at a
 * time: {@code costBasisStrategy} null leaves the lot's whole cost basis — strategy, anchor,
 * params, and rate bands — untouched. Naming a strategy replaces the basis atomically with
 * whatever anchor/params/bands accompany it in the same request, because a param left over from
 * a different strategy would be a latent bug rather than a real "no change". There is currently
 * no way to clear a declared basis back to none; only to replace it with another.
 *
 * <p>Applying any of these is guarded by {@code LotEditPolicy} — once stock has been consumed
 * from the lot, its costs are already recorded against sales and none of these fields may move.
 * Changing the cost basis on an editable lot re-derives and re-pins every batch in it.
 */
public record UpdateLotRequest(
        String supplierId,
        String receivedOn,
        Long amountPaidPaise,
        Long freightPaise,
        AllocationMethod allocationMethod,
        String categoryCode,
        CostBasisStrategy costBasisStrategy,
        CostAnchor costAnchor,
        Long flatUnitCostPaise,
        Long percentBp,
        Long multiplierMilli,
        MultiplierBase multiplierBase,
        List<MrpRateBand> rateBands) {

    public UpdateLotRequest {
        if (amountPaidPaise != null && amountPaidPaise <= 0) {
            throw new IllegalArgumentException("amountPaidPaise must be greater than 0");
        }
        if (freightPaise != null && freightPaise < 0) {
            throw new IllegalArgumentException("freightPaise must be non-negative");
        }
        if (flatUnitCostPaise != null && flatUnitCostPaise <= 0) {
            throw new IllegalArgumentException("flatUnitCostPaise must be greater than 0");
        }
        if (percentBp != null && percentBp <= 0) {
            throw new IllegalArgumentException("percentBp must be greater than 0");
        }
        if (multiplierMilli != null && multiplierMilli <= 0) {
            throw new IllegalArgumentException("multiplierMilli must be greater than 0");
        }
        rateBands = rateBands == null ? List.of() : List.copyOf(rateBands);
    }
}
