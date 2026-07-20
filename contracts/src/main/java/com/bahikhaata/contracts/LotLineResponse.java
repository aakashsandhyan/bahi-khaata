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
 * What one line of a received lot cost.
 *
 * @param batchId the batch created for it
 * @param productId the product
 * @param quantityReceived how many arrived
 * @param quantityDamaged how many cannot be sold
 * @param allocatedTotalPaise this line's share of the lot amount — the figure that reconciles
 * @param allocatedUnitCostPaise that share over the sellable quantity, rounded down
 * @param costBasis how the cost was arrived at
 */
public record LotLineResponse(
        String batchId,
        String productId,
        long quantityReceived,
        long quantityDamaged,
        long allocatedTotalPaise,
        long allocatedUnitCostPaise,
        CostBasis costBasis) {}
