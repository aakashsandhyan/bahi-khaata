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
 * One product within a consignment lot.
 *
 * <p>Carries no MRP. A manifest states what goods cost or what they sold for online, and
 * neither is the maximum retail price printed on the pack — that is read off the goods when
 * they are unpacked, and until then the product cannot be sold.
 *
 * @param code the supplier's identifier for the product, used as its barcode
 * @param name the product name
 * @param quantity how many arrived
 * @param weighingValuePaise what this line is worth relative to its neighbours, for splitting
 *     the lot cost — a selling price for returns, a supplier cost for ordinary supply
 * @param pinnedUnitCostPaise a cost the supplier itemised, or null to have it apportioned
 * @param trackingNumber the box it arrived in, for checking the delivery is complete
 */
public record ImportLine(
        String code,
        String name,
        long quantity,
        long weighingValuePaise,
        Long pinnedUnitCostPaise,
        String trackingNumber) {}
