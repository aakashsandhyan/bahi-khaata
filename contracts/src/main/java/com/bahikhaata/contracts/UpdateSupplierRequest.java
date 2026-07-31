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
 * Editing an existing supplier's details. Activation state is changed through its own
 * deactivate/reactivate endpoints, not here, so an edit never silently retires a vendor.
 */
public record UpdateSupplierRequest(
        String name,
        String gstin,
        String phone,
        String address,
        String contactPerson,
        String notes) {

    public UpdateSupplierRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("supplier name required");
        }
        Gstin.requireValidOrAbsent(gstin);
    }
}
