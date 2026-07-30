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

import java.util.UUID;

/**
 * The already-counted product a scanned LSN/ASIN resolved to, opened for pricing. Carries what the
 * workbench needs to price it: its batch, whether that batch is costed (drives whether a price can
 * be suggested), its unit cost when costed, and any MRP already recorded (and whether it is still
 * an estimate to be confirmed here).
 */
public record ScannedItem(
        UUID productId,
        String name,
        String categoryCode,
        UUID batchId,
        boolean costed,
        Long unitCostPaise,
        Long mrpPaise,
        boolean mrpIsEstimate) {}
