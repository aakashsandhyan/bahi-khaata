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
 * How many units of a product are available to sell.
 *
 * <p>Derived from the stock ledger, never a stored counter, so it always agrees with the
 * movements that produced it.
 *
 * @param productId the product asked about
 * @param quantityOnHand units available
 * @param asAt the moment the figure describes, in ISO-8601 UTC — null means as things stand now
 */
public record StockOnHandResponse(String productId, long quantityOnHand, String asAt) {}
