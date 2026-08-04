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
 * How much of what was received has been given a shelf price, and how much still waits.
 *
 * @param receivedUnits all-time {@code SUM(quantity)} over ledger rows with
 *     {@code movement_type = 'PURCHASE_RECEIPT'}
 * @param pricedUnits on-hand units for products carrying a {@code selling_price_paise}
 * @param unpricedBacklogUnits on-hand counted units for products still lacking a
 *     {@code selling_price_paise} — the Pricing screen's queue
 */
public record ReceivedVsPricedKpi(long receivedUnits, long pricedUnits, long unpricedBacklogUnits) {}
