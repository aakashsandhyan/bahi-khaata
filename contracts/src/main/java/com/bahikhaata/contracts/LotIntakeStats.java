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
 * A lot's Intake-screen aggregate: the money and count figures the header stats and the lot-math
 * rail need, from one call (design decision D5 of palletworks-intake). Nothing here is a manifest
 * total — the manifest states cost, not MRP (design.md context), so {@code mrpFoundPaise} is the
 * cumulative MRP actually read off counted goods, not a stated figure (D6).
 *
 * <p>Fields that would otherwise require a division by an as-yet-zero denominator are held
 * nullable rather than computed as zero or infinity: an empty lot answers honest nulls, never a
 * fabricated ratio (D5, and the pallet-intake spec's zero-counted scenarios).
 *
 * @param paidPaise what the lot was bought for — {@code lot.amountPaid}, freight excluded, the
 *     same figure {@link com.bahikhaata.contracts.LotCostReconciliation#amountPaidPaise()} reports
 * @param pinnedPaise the sum of pinned unit costs times quantity received, over costed batches —
 *     the same figure {@code LotClosing.crossCheckCost} reports, exposed here because no endpoint
 *     surfaced it before this change (design.md context); the Reconcile & close tab's
 *     paid-versus-pinned cross-check reads it from here (D9)
 * @param mrpFoundPaise the cumulative MRP read off counted units so far — rises as counting
 *     continues, never a manifest total (D6)
 * @param costOfMrpPercent paid divided by MRP found, as a whole-number percent, labelled
 *     provisional by the caller; null while {@code mrpFoundPaise} is zero, so the header shows a
 *     dash rather than a divide-by-zero (D5, D6)
 * @param expectedUnits the manifest's total expected quantity for the lot, summed across every
 *     expected line; null only when the lot has no expected lines at all yet (an empty manual lot
 *     nothing has been added to) — never fabricated as zero
 * @param countedUnits the total counted quantity for the lot, summed across every expected line —
 *     always a real figure, zero when genuinely nothing has been counted
 * @param shortUnits the sum, line by line, of expected minus counted where counted falls short
 * @param overUnits the sum, line by line, of counted minus expected where more turned up than the
 *     manifest claimed
 * @param effectiveCostPerUnitPaise amount paid divided by counted units; null while counted units
 *     is zero, so the rail shows a dash rather than a divide-by-zero (D5)
 * @param projectedRetailPaise the sum, over priced batches only, of each batch's selling price
 *     times its sellable quantity — never extrapolated across unpriced units
 */
public record LotIntakeStats(
        UUID lotId,
        long paidPaise,
        long pinnedPaise,
        long mrpFoundPaise,
        Integer costOfMrpPercent,
        Long expectedUnits,
        long countedUnits,
        long shortUnits,
        long overUnits,
        Long effectiveCostPerUnitPaise,
        long projectedRetailPaise) {}
