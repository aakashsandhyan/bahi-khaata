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
 * A lot's cost cross-check for reporting: what was paid against the sum of its products' pinned
 * costs times the quantities received. A non-zero difference is surfaced, never absorbed into the
 * goods — cost is per product, and the amount paid is a fact to reconcile to, not a pool to divide.
 */
public record LotCostReconciliation(
        UUID lotId, long amountPaidPaise, long pinnedTotalPaise, long differencePaise, boolean reconciles) {}
