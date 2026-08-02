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

import java.util.Map;

/**
 * A request to create a product.
 *
 * <p>No price: a product is created unpriced and given a price later by a deliberate action.
 * That mirrors how stock actually arrives — received before it has been valued.
 *
 * @param name product name
 * @param category one of the governed categories
 * @param hsnCode HSN code, or null if not known
 * @param attributes category-specific attributes, or null if none
 */
public record CreateProductRequest(
        String name, String category, String hsnCode, Map<String, Object> attributes) {}
