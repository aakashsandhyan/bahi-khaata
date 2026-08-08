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

/**
 * Correcting a lot's data-entry fields — every field is a partial update, not a replacement.
 *
 * <p>{@code null} on any field means "leave it as it is"; there is no other way to send "no
 * change" over JSON for a field that also has a legitimate empty value. {@code categoryCode} is
 * the one exception: {@code null} leaves the lot's category alone, but {@code ""} explicitly
 * clears it back to "no default", since a lot losing its category is a real, distinct choice
 * from simply not mentioning it.
 *
 * <p>Applying any of these is guarded by {@code LotEditPolicy} — once stock has been consumed
 * from the lot, its costs are already recorded against sales and none of these fields may move.
 */
public record UpdateLotRequest(
        String supplierId,
        String receivedOn,
        Long amountPaidPaise,
        Long freightPaise,
        AllocationMethod allocationMethod,
        String categoryCode) {

    public UpdateLotRequest {
        if (amountPaidPaise != null && amountPaidPaise <= 0) {
            throw new IllegalArgumentException("amountPaidPaise must be greater than 0");
        }
        if (freightPaise != null && freightPaise < 0) {
            throw new IllegalArgumentException("freightPaise must be non-negative");
        }
    }
}
