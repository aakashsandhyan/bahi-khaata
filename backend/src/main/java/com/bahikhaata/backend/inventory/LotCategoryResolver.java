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
package com.bahikhaata.backend.inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The category a lot belongs to, for labelling a list of lots.
 *
 * <p>A consignment is imported as one lot per category, so a lot holds a single category and any of
 * its goods names it. This is derived rather than stored: the category lives on the products, and a
 * lot is only ever "KITCHEN" because everything in it is. It exists so a list of same-supplier,
 * same-day lots — seven Sushil lots received 2026-07-17, say — can be told apart at a glance.
 *
 * <p>Both sources are read at once and folded into a map, so labelling a page of lots is two
 * queries rather than two per lot. A manifest line is the primary source; batches cover a manual
 * lot that has stock but no manifest. A lot with neither is absent from the map and left unlabelled.
 */
@Component
public class LotCategoryResolver {

    private final ExpectedLineRepository expectedLines;
    private final BatchRepository batches;

    LotCategoryResolver(ExpectedLineRepository expectedLines, BatchRepository batches) {
        this.expectedLines = expectedLines;
        this.batches = batches;
    }

    /** Lot id to its category code. A lot with no stock and no manifest line is not present. */
    public Map<UUID, String> categoryByLot() {
        Map<UUID, String> byLot = new HashMap<>();
        // Batches first, then let a manifest line's category win where both exist — they agree for a
        // manifest lot, and this leaves a manual lot labelled from its batches.
        for (Object[] row : batches.categoryCodeByLot()) {
            byLot.put((UUID) row[0], (String) row[1]);
        }
        for (Object[] row : expectedLines.categoryCodeByLot()) {
            byLot.put((UUID) row[0], (String) row[1]);
        }
        return byLot;
    }
}
