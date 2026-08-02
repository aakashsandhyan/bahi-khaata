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
 * An online marketplace whose selling price we have observed.
 *
 * <p>Recorded beside an observed price so it can be read in context: what a thing fetches on
 * one marketplace is not what it fetches on another, and a bare number with no source cannot
 * be judged.
 *
 * <p>A marketplace price is <em>not</em> an MRP. MRP is the printed legal ceiling; this is one
 * seller's asking price on one day, and it may sit either side of that ceiling. It informs a
 * shelf price and never authorises one — see {@code Product.isSellable}.
 *
 * <p>Stored by name in {@code product.online_price_source}, which carries a {@code CHECK}
 * constraint over exactly these values; a drift test holds the two in step.
 */
public enum Marketplace {
    AMAZON,
    FLIPKART
}
