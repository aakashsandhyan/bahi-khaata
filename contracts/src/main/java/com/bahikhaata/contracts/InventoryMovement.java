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
 * One line of a product's movement log, read straight from {@code stock_ledger} — the item
 * detail's evidence trail (design decision D3 of palletworks-inventory).
 *
 * @param movementType PURCHASE_RECEIPT, SALE, WRITE_OFF, or ADJUSTMENT
 * @param quantity signed — positive is stock arriving, negative is stock leaving
 * @param cogsPaise cost of goods sold, or null for an inward movement
 */
public record InventoryMovement(
        String movementType, long quantity, Long cogsPaise, String effectiveAt) {}
