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
 * Revenue for the current IST calendar day, and the bill count that produced it.
 *
 * @param totalPaise {@code SUM(sale.total_paise)} for sales whose {@code created_at} falls in
 *     today's IST window
 * @param billCount how many sales fall in that window
 * @param averagePaise {@code totalPaise / billCount}; null when {@code billCount} is zero, never a
 *     bare ₹0 — a zero average would misread as "bills average nothing" rather than "no bills yet"
 */
public record RevenueTodayKpi(long totalPaise, long billCount, Long averagePaise) {}
