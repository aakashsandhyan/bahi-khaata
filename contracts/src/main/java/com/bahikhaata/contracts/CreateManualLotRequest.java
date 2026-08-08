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
 * The cost-basis fields below are all optional and travel together: {@code costBasisStrategy}
 * null means the lot declares no basis and keeps today's apportionment/rate behaviour; naming a
 * strategy asks for the whole basis — its anchor, its params, its bands — to be given at once,
 * since the params only make sense as a set for the one strategy chosen. Full per-strategy
 * validation (which params a given strategy actually needs) lives in the backend, not here — a
 * compact record constructor cannot know which fields a not-yet-declared strategy requires.
 */
public record CreateManualLotRequest(
        String supplierId,
        String receivedOn,
        long amountPaidPaise,
        AllocationMethod allocationMethod,
        String categoryCode,
        CostBasisStrategy costBasisStrategy,
        CostAnchor costAnchor,
        Long flatUnitCostPaise,
        Long percentBp,
        Long multiplierMilli,
        MultiplierBase multiplierBase,
        List<MrpRateBand> rateBands) {

    public CreateManualLotRequest {
        if (supplierId == null || supplierId.isBlank()) {
            throw new IllegalArgumentException("supplierId required");
        }
        if (receivedOn == null || receivedOn.isBlank()) {
            throw new IllegalArgumentException("receivedOn required");
        }
        if (amountPaidPaise <= 0) {
            throw new IllegalArgumentException("amountPaidPaise must be greater than 0");
        }
        if (allocationMethod == null) {
            throw new IllegalArgumentException("allocationMethod required");
        }
        // categoryCode is optional: a manual lot with no products yet may not have one chosen.
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
