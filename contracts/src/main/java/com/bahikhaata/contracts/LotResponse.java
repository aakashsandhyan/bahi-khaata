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
 * A received lot and the costs allocated across it.
 *
 * @param lotId the lot
 * @param supplier who it came from
 * @param receivedOn the delivery date, ISO-8601
 * @param amountPaidPaise what was paid
 * @param freightPaise freight, included in what was allocated
 * @param totalAllocatedPaise amount paid plus freight — the line totals sum to exactly this
 * @param allocationMethod how the figures were produced
 * @param lines what each line ended up costing
 */
public record LotResponse(
        String lotId,
        String supplier,
        String receivedOn,
        long amountPaidPaise,
        long freightPaise,
        long totalAllocatedPaise,
        AllocationMethod allocationMethod,
        List<LotLineResponse> lines) {}
