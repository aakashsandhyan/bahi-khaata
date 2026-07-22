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

import java.util.UUID;

/**
 * A product ready to be priced, with everything a decision needs beside it.
 *
 * <p>Cost is what we paid, known only once the delivery is closed. The online price is what the
 * goods fetched on a marketplace — the figure the shop's price must beat, since the promise is
 * to be cheaper than online. MRP is the printed legal ceiling, above which nothing may be sold.
 *
 * @param unitCostPaise what one of these cost us
 * @param onlinePricePaise what it last sold for online, or null if unknown — the price to beat
 * @param mrpPaise the printed maximum retail price, the legal ceiling
 * @param mrpIsEstimate whether the MRP was read off the goods or looked up
 * @param currentPricePaise the price it sells at now, or null if not yet priced
 * @param condition GOOD or DAMAGED — damaged goods are priced separately, at their own figure
 */
public record PriceableItem(
        UUID productId,
        String name,
        String categoryCode,
        long unitCostPaise,
        Long onlinePricePaise,
        Long mrpPaise,
        boolean mrpIsEstimate,
        Long currentPricePaise,
        String condition) {}
