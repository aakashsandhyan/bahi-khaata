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
package com.bahikhaata.backend.catalog;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BarcodeRepository extends JpaRepository<Barcode, UUID> {

    /** A scanned code to its barcode, or empty if unrecognised. */
    Optional<Barcode> findByCode(String code);

    /**
     * The checkout hot path: a scanned code straight to its product, or empty if the code is
     * unrecognised. Returns the product itself rather than a lazy association off a barcode,
     * so the caller holds a usable product outside the persistence session.
     */
    @Query("SELECT b.product FROM Barcode b WHERE b.code = :code")
    Optional<Product> findProductByCode(@Param("code") String code);
}
