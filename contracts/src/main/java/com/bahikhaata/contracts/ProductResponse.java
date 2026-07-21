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

import java.util.Map;

/**
 * A product as seen on the wire.
 *
 * <p>Money is an integer count of paise, never a decimal, for the same exactness reason the
 * backend stores it that way. An unpriced product carries a null {@code sellingPricePaise} —
 * absence is represented as absence, not as zero, so a client can never mistake "not yet
 * priced" for "free".
 *
 * @param id product identifier
 * @param name product name
 * @param category one of the governed categories
 * @param sellingPricePaise selling price in paise, or null when the product is unpriced
 * @param hsnCode HSN code, or null when not recorded
 * @param attributes category-specific attributes, or null when none
 * @param priceReviewFlagged whether the product is flagged for a margin review
 */
public record ProductResponse(
        String id,
        String name,
        String category,
        Long sellingPricePaise,
        String hsnCode,
        Map<String, Object> attributes,
        boolean priceReviewFlagged) {}
