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
 * What one printed label carries — the shop's single label per product: wordmark, barcode, name,
 * and the price story.
 *
 * <p>{@code mrpPaise} is null when no confirmed MRP exists; the label then shows the price alone,
 * with no strike-through and no saving claimed — an estimate never prints as the legal figure. The
 * saving percentage is derived from the two figures at render time, not carried separately, so the
 * label can never claim a discount that disagrees with its own numbers.
 */
public record PrintLabelRequest(
        String barcode, String productName, Long mrpPaise, long pricePaise) {}
