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
 * One row of the product catalogue: a product, whether it has been found, whether it is priced, and
 * how much of it the manifest expects versus how much has been counted.
 *
 * <p>The same product (marketplace reference) is routinely listed on several boxes' sheets, so
 * {@code unitsExpected} and {@code unitsCounted} are summed across every expected line for it — the
 * whole delivery's worth. {@code unitsExpected - unitsCounted} is what is still on paper: units the
 * manifest owes that nobody has found yet.
 */
public record CatalogEntry(
        UUID productId,
        String name,
        String categoryCode,
        CatalogStatus status,
        boolean priced,
        long unitsExpected,
        long unitsCounted) {}
