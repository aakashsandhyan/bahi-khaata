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
 * A request to reclassify a product's category (department).
 *
 * <p>A plain reclassification, deliberately separate from pricing: it carries no price and no
 * batch, and setting it neither reprices the product nor moves stock (design decision D4 of
 * palletworks-nav). {@code Product.setCategory} is the only thing it touches.
 *
 * @param categoryCode the category's stable identifier, such as {@code KITCHEN}
 */
public record SetCategoryRequest(String categoryCode) {}
