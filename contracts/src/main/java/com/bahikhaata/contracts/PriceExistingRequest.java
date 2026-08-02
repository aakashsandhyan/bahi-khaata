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

import java.util.UUID;

/**
 * Prices a scanned, already-counted product: set its category and selling price, confirm the
 * batch MRP (null to leave none), mint a BBZ if it has none. Writes no stock — the stock was
 * already received at counting.
 */
public record PriceExistingRequest(
        UUID productId,
        UUID batchId,
        String categoryCode,
        long sellingPricePaise,
        Long mrpPaise,
        // The true in-hand count taken at pricing: the total on a first pricing (overwrites stock),
        // or the pieces found on a later one (added). Null/absent means leave stock untouched.
        Long inHandQuantity,
        // A corrected product name, or null to leave it. The reviewer may fix a messy manifest name.
        String name,
        // When true, inHandQuantity is the true total and overwrites on-hand however the product was
        // priced before — the reviewer's count of record. When false, the first-vs-later rule
        // applies (first pricing overwrites, a later one adds).
        boolean setInHandAsTotal,
        // Who priced it (remembered per device), shown on the review screen. Null when not set.
        String operatorName) {}
