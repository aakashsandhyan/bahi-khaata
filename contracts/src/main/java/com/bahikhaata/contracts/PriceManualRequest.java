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

import java.util.UUID;

/**
 * Prices stock keyed in by hand — never counted, or a lost-reference item re-entered. Creates the
 * product and a batch under the lot for {@code quantity} units in {@code condition} ("GOOD" or
 * "DAMAGED"), writes the stock receipt, sets category and selling price, records the MRP confirmed
 * (null for none), and mints a BBZ. An uncosted batch has no suggestion, so the price is required.
 */
public record PriceManualRequest(
        UUID lotId,
        String name,
        String categoryCode,
        String condition,
        long quantity,
        long sellingPricePaise,
        Long mrpPaise,
        // Who priced it (remembered per device), shown on the review screen. Null when not set.
        String operatorName,
        // Where the newly-materialised batch's stock physically sits, or null to leave it unset
        // (design decision D8 of palletworks-inventory).
        String bin) {}
