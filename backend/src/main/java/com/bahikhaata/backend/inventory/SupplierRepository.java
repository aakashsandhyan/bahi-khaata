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
package com.bahikhaata.backend.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    /** Identity lookup on the normalised name — the fallback key when a vendor has no GSTIN. */
    Optional<Supplier> findByNameNormalized(String nameNormalized);

    /** Uniqueness guard for a registered vendor's GSTIN. */
    Optional<Supplier> findByGstin(String gstin);

    /** The pick-list at receipt: active vendors only, alphabetical. */
    List<Supplier> findByActiveTrueOrderByNameAsc();

    List<Supplier> findAllByOrderByNameAsc();
}
