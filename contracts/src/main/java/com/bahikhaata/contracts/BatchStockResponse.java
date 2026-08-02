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
package com.bahikhaata.contracts;

/**
 * What remains of one batch, and what it cost.
 *
 * <p>Listed oldest delivery first, which is the order stock is consumed in. The per-batch cost
 * is why the breakdown is worth having at all: it is what makes a delivery's margin
 * answerable after the fact.
 *
 * @param batchId the batch
 * @param lotId the purchase it arrived in
 * @param receivedOn the lot's delivery date, ISO-8601
 * @param quantityRemaining units still drawable from this batch
 * @param allocatedUnitCostPaise what one unit of it cost, in paise
 * @param costBasis how that cost was arrived at
 */
public record BatchStockResponse(
        String batchId,
        String lotId,
        String receivedOn,
        long quantityRemaining,
        long allocatedUnitCostPaise,
        CostBasis costBasis) {}
