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
 * One entry of a product's price history, read newest-first as part of item detail (design
 * decisions D3/D5 of palletworks-inventory).
 *
 * @param oldPricePaise the price immediately before this change, or null on a product's
 *     first-ever price set — never a fabricated zero
 * @param operatorName who made the change, or null — not captured at the pricing choke point yet
 *     (design decision D6)
 */
public record PriceChange(
        Long oldPricePaise, long newPricePaise, String operatorName, String changedAt) {}
