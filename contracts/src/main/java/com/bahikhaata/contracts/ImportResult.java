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

import java.util.List;

/**
 * What an import actually recorded.
 *
 * @param lotsCreated one per category
 * @param productsCreated products that did not exist before
 * @param productsMatched products already in the catalogue, matched by code
 * @param unitsReceived total units brought on hand
 * @param totalAllocatedPaise what was apportioned, which equals what was paid
 * @param awaitingMrp products that cannot be sold until an MRP is read off them
 * @param warnings anything worth a human's attention
 */
public record ImportResult(
        int lotsCreated,
        int productsCreated,
        int productsMatched,
        long unitsReceived,
        long totalAllocatedPaise,
        int awaitingMrp,
        List<String> warnings) {}
