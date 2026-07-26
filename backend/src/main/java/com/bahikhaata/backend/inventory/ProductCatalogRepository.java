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

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.contracts.Origin;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The catalogue's own reads over {@link Product}, kept in the inventory package on purpose.
 *
 * <p>Whether a product has been found is decided by whether it has a counted {@link Batch} or a
 * scannable {@link com.bahikhaata.backend.catalog.Barcode} — one entity from each side. The allowed
 * dependency runs inventory → catalog, never back, so a query that reads {@code Batch} cannot live in
 * the catalog package. It lives here, beside {@link GoodsRemediation}, which reads products, batches,
 * and barcodes together for the same reason.
 *
 * <p>Status is expressed as {@code EXISTS} sub-selects rather than derived in Java after loading, so
 * the filter and the paging both happen in the database — a list ordered by a value computed after
 * the query could not be paged. A second repository over {@code Product} is deliberate; Spring Data
 * allows more than one, and this keeps the catalogue's status-aware reads apart from the plain ones.
 */
public interface ProductCatalogRepository extends JpaRepository<Product, UUID> {

    /**
     * On-paper products whose name matches: no counted batch, and no code other than a marketplace
     * reference. These are the goods a delivery still owes that nobody has found.
     */
    @Query(
            "SELECT p FROM Product p "
                    + "WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "AND (:category = '' OR p.categoryCode = :category) "
                    + "AND NOT EXISTS (SELECT 1 FROM Batch b WHERE b.product = p) "
                    + "AND NOT EXISTS (SELECT 1 FROM Barcode bc WHERE bc.product = p "
                    + "AND bc.origin <> :marketplace) "
                    + "ORDER BY p.name")
    List<Product> findOnPaper(
            @Param("q") String q,
            @Param("category") String category,
            @Param("marketplace") Origin marketplace,
            Pageable pageable);

    /**
     * Found products whose name matches: at least one counted batch, or at least one physical code
     * (anything but a marketplace reference).
     */
    @Query(
            "SELECT p FROM Product p "
                    + "WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "AND (:category = '' OR p.categoryCode = :category) "
                    + "AND (EXISTS (SELECT 1 FROM Batch b WHERE b.product = p) "
                    + "OR EXISTS (SELECT 1 FROM Barcode bc WHERE bc.product = p "
                    + "AND bc.origin <> :marketplace)) "
                    + "ORDER BY p.name")
    List<Product> findFound(
            @Param("q") String q,
            @Param("category") String category,
            @Param("marketplace") Origin marketplace,
            Pageable pageable);

    /** Every product whose name matches and is in the category, found or not, ordered by name. */
    @Query(
            "SELECT p FROM Product p "
                    + "WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "AND (:category = '' OR p.categoryCode = :category) "
                    + "ORDER BY p.name")
    List<Product> findByName(
            @Param("q") String q, @Param("category") String category, Pageable pageable);

    /**
     * Which of the given products have a counted batch — half of "found", asked in bulk so a mixed
     * (all) page can be marked without a query per row.
     */
    @Query("SELECT DISTINCT b.product.id FROM Batch b WHERE b.product.id IN :ids")
    Set<UUID> foundByBatch(@Param("ids") Collection<UUID> ids);

    /** Which of the given products have a physical (non-marketplace) code — the other half. */
    @Query(
            "SELECT DISTINCT bc.product.id FROM Barcode bc "
                    + "WHERE bc.product.id IN :ids AND bc.origin <> :marketplace")
    Set<UUID> foundByCode(
            @Param("ids") Collection<UUID> ids, @Param("marketplace") Origin marketplace);
}
