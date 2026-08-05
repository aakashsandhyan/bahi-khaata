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

/** The three stages of the Dashboard's received → priced → sold funnel, in that order. */
public enum DashboardFunnelStage {
    /** Units received: {@code SUM(quantity)} where {@code movement_type = 'PURCHASE_RECEIPT'}. */
    RECEIVED,

    /** On-floor units: on-hand units for products carrying a {@code selling_price_paise}. */
    PRICED,

    /** Units sold: {@code SUM(-quantity)} where {@code movement_type = 'SALE'}. */
    SOLD
}
