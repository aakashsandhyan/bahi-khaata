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

import com.bahikhaata.contracts.StockCondition;
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
     * The ids of lots that hold at least one counted-but-unpriced product — the pricing workbench's
     * queue. Open lots (uncosted, hand-priced) and closed lots (costed, margin-suggested) both
     * appear; a lot drops off once everything in it is priced.
     */
    @Query("select distinct b.lot.id from Batch b where b.product.sellingPrice is null")
    List<UUID> lotIdsWithUnpricedStock();

    /**
     * Each lot paired with a category of its stock. Labels a lot that has batches but no manifest
     * lines — a manual lot — where {@link ExpectedLineRepository#categoryCodeByLot()} finds nothing.
     */
    @Query("SELECT DISTINCT b.lot.id, b.product.categoryCode FROM Batch b")
    List<Object[]> categoryCodeByLot();

    /**
     * The one batch a product has within a lot. Unique by constraint: counting the same
     * product out of several cartons of one delivery accumulates into a single batch, since a
     * batch is one product's arrival in one lot however many boxes it was split between.
     */
    /**
     * A product's batches within a lot — one per condition it arrived in, so at most two.
     * Sound and damaged goods are held apart because they sell for different amounts and the
     * ledger has to be able to say which kind left.
     */
    List<Batch> findByLotIdAndProductId(UUID lotId, UUID productId);

    /** Every batch of a product across its deliveries, for the state breakdown a rescue works from. */
    List<Batch> findByProductId(UUID productId);

    /** Held stock in a condition — used to gather the needs-work backlog (quantity above zero). */
    List<Batch> findByConditionAndQuantityReceivedGreaterThan(
            StockCondition condition, long quantity);

    Optional<Batch> findByLotIdAndProductIdAndCondition(
            UUID lotId, UUID productId, StockCondition condition);

    /**
     * A needs-work batch is keyed also by the kind of work it needs, since one product may hold
     * units under different kinds at once. The other three conditions have a null issue type and
     * are looked up by the method above.
     */
    Optional<Batch> findByLotIdAndProductIdAndConditionAndIssueType(
            UUID lotId, UUID productId, StockCondition condition, String issueType);

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
