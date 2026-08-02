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

import java.util.List;

/**
 * What an import actually recorded.
 *
 * <p>Note what is absent: nothing about stock. An import records what a supplier claims is
 * coming, and claims put nothing on hand. Units appear when someone opens a box and counts.
 *
 * @param lotsCreated one per category
 * @param boxesCreated distinct cartons expected, by tracking number
 * @param expectedLinesCreated one per product per box — more than the product count, since a
 *     product routinely arrives split across several cartons
 * @param productsCreated products that did not exist before
 * @param productsMatched products already in the catalogue, matched by code
 * @param unitsExpected total units the supplier says are coming, none of them on hand
 * @param warnings anything worth a human's attention
 */
public record ImportResult(
        int lotsCreated,
        int boxesCreated,
        int expectedLinesCreated,
        int productsCreated,
        int productsMatched,
        long unitsExpected,
        List<String> warnings) {}
