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
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BatchRepository extends JpaRepository<Batch, UUID> {

    /** Every batch of a lot — the lines whose costs must reconcile to the lot amount. */
    List<Batch> findByLotId(UUID lotId);

    /**
     * The one batch a product has within a lot. Unique by constraint: counting the same
     * product out of several cartons of one delivery accumulates into a single batch, since a
     * batch is one product's arrival in one lot however many boxes it was split between.
     */
    Optional<Batch> findByLotIdAndProductId(UUID lotId, UUID productId);

    /**
     * A product's batches in FIFO order: oldest delivery first, by the lot's business date
     * rather than when the row happened to be created, so a late-logged delivery still
     * consumes in true arrival order. Creation time breaks ties within a single day.
     */
    @Query("SELECT b FROM Batch b WHERE b.product.id = :productId "
            + "ORDER BY b.lot.receivedOn ASC, b.createdAt ASC")
    List<Batch> findByProductIdInFifoOrder(@Param("productId") UUID productId);

    /**
     * A product's batches newest delivery first — the reverse of FIFO order.
     *
     * <p>Used for the MRP a customer should see: successive lots of the same product genuinely
     * arrive bearing different printed prices, and the one on the shelf now is the one that
     * came in last.
     */
    @Query("SELECT b FROM Batch b WHERE b.product.id = :productId "
            + "ORDER BY b.lot.receivedOn DESC, b.createdAt DESC")
    List<Batch> findByProductIdNewestFirst(@Param("productId") UUID productId);
}
