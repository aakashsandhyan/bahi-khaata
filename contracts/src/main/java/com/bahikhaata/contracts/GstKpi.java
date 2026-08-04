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
 * GST collected, all-time.
 *
 * <p>Reads {@code SUM(sale.tax_paise)}, which sums to zero for every sale today: the shop bills as
 * a composition Bill of Supply and collects no tax until the separate gst-inclusive-pricing change
 * ships. {@code computed} is that fact made explicit, so the tile can label its ₹0 "not yet
 * computed" rather than let it read as a real revenue-neutral figure.
 *
 * @param taxAllTimePaise {@code SUM(sale.tax_paise)} across every sale
 * @param computed always false for now; flip once GST is actually calculated per line
 */
public record GstKpi(long taxAllTimePaise, boolean computed) {}
