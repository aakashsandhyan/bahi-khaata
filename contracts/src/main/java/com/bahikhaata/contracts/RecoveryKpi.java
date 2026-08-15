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
 * All-time recovery on lot spend: what has come in at the till against what was paid for goods.
 *
 * <p>Excludes {@code lot.freight_paise} — this reads against the "amount paid" figure operators
 * recognise, slightly overstating recovery versus true landed cost. A landed-cost variant is
 * deferred to the Analytics phase.
 *
 * @param revenuePaise all-time {@code SUM(sale.total_paise)}
 * @param paidPaise all-time {@code SUM(lot.amount_paid_paise)}
 * @param ratio {@code revenuePaise ÷ paidPaise}; null when {@code paidPaise} is zero (no lots yet),
 *     never a divide-by-zero
 */
public record RecoveryKpi(long revenuePaise, long paidPaise, Double ratio) {}
