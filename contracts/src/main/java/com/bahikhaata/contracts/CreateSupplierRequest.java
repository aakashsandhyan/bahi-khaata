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
 * Registering a new supplier from the dashboard. Name is required; everything else is optional,
 * because many small vendors have nothing more than a name.
 */
public record CreateSupplierRequest(
        String name,
        String gstin,
        String phone,
        String address,
        String contactPerson,
        String notes) {

    public CreateSupplierRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("supplier name required");
        }
        Gstin.requireValidOrAbsent(gstin);
    }
}
