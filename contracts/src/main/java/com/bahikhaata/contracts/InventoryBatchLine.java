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
 * One batch in a product's item-detail per-batch list — the breakdown an Inventory row's lot
 * rollup deliberately hides (design decision D1 of palletworks-inventory).
 *
 * @param bin where this batch's stock physically sits, or null if nobody has set one
 * @param allocatedUnitCostPaise this batch's own unit cost, or null while its lot is still open
 * @param mrpPaise the printed maximum retail price recorded on this batch, or null if unread
 */
public record InventoryBatchLine(
        UUID batchId,
        String condition,
        String lotLabel,
        String bin,
        long quantityReceived,
        long quantityDamaged,
        Long allocatedUnitCostPaise,
        Long mrpPaise,
        String createdAt) {}
