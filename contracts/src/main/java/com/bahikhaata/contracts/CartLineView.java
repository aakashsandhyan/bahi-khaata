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
 * One product on the till's cart, with the saving spelt out.
 *
 * <p>The saving is the shop's whole proposition, so it travels beside the price rather than being
 * derived on screen — the MRP struck through, the price paid, and how much and what fraction was
 * saved against it.
 */
public record CartLineView(
        UUID lineId,
        UUID productId,
        String name,
        String asin,
        long mrpPaise,
        long unitPricePaise,
        long quantity,
        long lineTotalPaise,
        long savingPaise,
        int savingPercent) {}
