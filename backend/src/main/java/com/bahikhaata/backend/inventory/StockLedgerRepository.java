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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockLedgerRepository extends JpaRepository<StockLedgerEntry, UUID> {

    /** A product's movements in effective order — the basis for on-hand and valuation. */
    List<StockLedgerEntry> findByProductIdOrderByEffectiveAtAscCreatedAtAsc(UUID productId);

    /** What has moved for one batch, which is how much of it remains. */
    List<StockLedgerEntry> findByBatchId(UUID batchId);

    /**
     * Quantity on hand for a product: the net of its movements, never a stored counter that
     * could disagree with them. COALESCE so a product that has never moved reports zero
     * rather than null.
     */
    @Query("SELECT COALESCE(SUM(e.quantity), 0) FROM StockLedgerEntry e "
            + "WHERE e.product.id = :productId")
    long quantityOnHand(@Param("productId") UUID productId);

    /** The same, for one batch — what FIFO needs to know is still drawable from it. */
    @Query("SELECT COALESCE(SUM(e.quantity), 0) FROM StockLedgerEntry e "
            + "WHERE e.batch.id = :batchId")
    long quantityOnHandForBatch(@Param("batchId") UUID batchId);

    /**
     * Quantity on hand as it stood at a moment in the past: movements effective after that
     * moment are excluded.
     *
     * <p>Comparison is on the stored ISO-8601 text, which is fixed width and therefore orders
     * chronologically — the reason the timestamp format pads its fractional seconds.
     */
    @Query("SELECT COALESCE(SUM(e.quantity), 0) FROM StockLedgerEntry e "
            + "WHERE e.product.id = :productId AND e.effectiveAt <= :asAt")
    long quantityOnHandAsAt(@Param("productId") UUID productId, @Param("asAt") Instant asAt);

    /** The same, for one batch — the basis for valuing stock as it stood. */
    @Query("SELECT COALESCE(SUM(e.quantity), 0) FROM StockLedgerEntry e "
            + "WHERE e.batch.id = :batchId AND e.effectiveAt <= :asAt")
    long quantityOnHandForBatchAsAt(@Param("batchId") UUID batchId, @Param("asAt") Instant asAt);

    /**
     * Whether any stock has left this lot, through any of its batches.
     *
     * <p>Asked of the whole lot, not one batch: allocation spreads a single amount across
     * every line, so once any of it has been consumed the figures for all of them are load
     * bearing.
     */
    @Query("SELECT COUNT(e) > 0 FROM StockLedgerEntry e "
            + "WHERE e.batch.lot.id = :lotId AND e.quantity < 0")
    boolean hasConsumptionFromLot(@Param("lotId") UUID lotId);
}
