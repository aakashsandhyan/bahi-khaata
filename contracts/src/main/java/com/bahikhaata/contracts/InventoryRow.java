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
import java.util.UUID;

/**
 * One row of the Inventory table: a product held in one stock condition, rolled up across
 * whichever lots back it (design decision D1 of palletworks-inventory).
 *
 * @param condition "GOOD" or "DAMAGED" — the only conditions that ever reach the stock ledger,
 *     so the only ones an inventory row can represent
 * @param lotLabel the single backing lot's identity ("supplier · received-on"), or an "N lots"
 *     marker when more than one lot backs this row
 * @param bins every distinct, non-null bin backing this row, aggregated across its batches — empty
 *     when nothing has a bin set
 * @param onHandQuantity the net stock-ledger sum for this product and condition; always positive,
 *     since a group netting to zero or below is excluded before it ever becomes a row
 * @param costBasisPaise the on-hand-weighted unit cost, or null when any contributing batch is not
 *     yet costed — an honest absence, never a fabricated zero
 * @param sellingPricePaise the price this condition sells at, or null if unpriced
 * @param marginPercent derived from price and cost basis, or null when either is unknown — never
 *     stored, always recomputed
 * @param ageDays whole days since the product's first PURCHASE_RECEIPT movement, as a UTC-day
 *     difference (design decision D4) — shared by every condition row of the same product
 */
public record InventoryRow(
        UUID productId,
        String productName,
        String condition,
        String lotLabel,
        List<String> bins,
        long onHandQuantity,
        Long costBasisPaise,
        Long sellingPricePaise,
        Integer marginPercent,
        long ageDays) {}
