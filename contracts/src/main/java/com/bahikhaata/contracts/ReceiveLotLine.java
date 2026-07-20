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
 * One product's arrival within a delivery.
 *
 * @param productId the product that arrived
 * @param quantityReceived how many, damaged included — this is what was paid for
 * @param quantityDamaged how many of those cannot be sold
 * @param mrpPaise printed retail price, or an estimate where none is printed
 * @param mrpIsEstimate whether that figure was estimated rather than read off the goods
 * @param pinnedUnitCostPaise a cost the supplier itemised, or null to have it apportioned
 */
public record ReceiveLotLine(
        String productId,
        long quantityReceived,
        long quantityDamaged,
        long mrpPaise,
        boolean mrpIsEstimate,
        Long pinnedUnitCostPaise) {}
