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
package com.bahikhaata.backend.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** Products with no selling price — the queue a manager works through to price stock. */
    List<Product> findBySellingPriceIsNull();

    /** Products whose name contains the query, for looking one up to change its stock's state. */
    List<Product> findTop25ByNameContainingIgnoreCaseOrderByName(String query);

    /**
     * Priced products whose label has not printed yet — the bulk-print queue. Priced means on the
     * shelf (a price is set); a null {@code labelPrintedAt} means no label has printed for it.
     */
    List<Product> findBySellingPriceIsNotNullAndLabelPrintedAtIsNullOrderByName();
}
