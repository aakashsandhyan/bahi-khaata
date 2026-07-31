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
package com.bahikhaata.backend.inventory;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExpectedLineRepository extends JpaRepository<ExpectedLine, UUID> {

    List<ExpectedLine> findByLotIdOrderByCode(UUID lotId);

    List<ExpectedLine> findByBoxIdOrderByCode(UUID boxId);

    Optional<ExpectedLine> findByBoxIdAndProductId(UUID boxId, UUID productId);

    Optional<ExpectedLine> findByLotIdAndCode(UUID lotId, String code);

    /** Every line for a product within one delivery, across its boxes — the product-centric grid. */
    List<ExpectedLine> findByLotIdAndProductIdOrderByCode(UUID lotId, UUID productId);

    /**
     * Each lot paired with a category one of its manifest lines carries. A lot holds one category —
     * a consignment is imported as a lot per category — so any line's category labels the whole lot.
     */
    @Query("SELECT DISTINCT el.lot.id, el.product.categoryCode FROM ExpectedLine el")
    List<Object[]> categoryCodeByLot();
}
