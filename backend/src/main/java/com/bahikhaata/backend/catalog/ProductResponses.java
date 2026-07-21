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
package com.bahikhaata.backend.catalog;

import com.bahikhaata.contracts.ProductResponse;

/**
 * Turns a {@link Product} entity into its wire form.
 *
 * <p>The one place the persistence model is translated to the contract, so a schema change
 * does not leak outward: the entity may gain or rename fields without the wire shape moving
 * unless this mapping says so.
 */
final class ProductResponses {

    private ProductResponses() {}

    static ProductResponse of(Product product) {
        return new ProductResponse(
                product.getId().toString(),
                product.getName(),
                product.getCategory().code(),
                // Null stays null: an unpriced product must not surface as zero.
                product.getSellingPrice() == null ? null : product.getSellingPrice().paise(),
                product.getHsnCode(),
                product.getAttributes(),
                product.isPriceReviewFlagged());
    }
}
