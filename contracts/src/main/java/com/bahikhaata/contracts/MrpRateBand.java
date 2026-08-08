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
 * One band of a lot's {@link CostBasisStrategy#MRP_RATE_RANGE} rate card: an item whose MRP
 * falls between {@code minMrpPaise} (inclusive) and {@code maxMrpPaise} (exclusive) costs
 * {@code costPaise}. A null {@code maxMrpPaise} is the open-topped final band, catching every MRP
 * at or above its minimum.
 *
 * <p>A child row on the lot rather than a JSON blob, so bands are queryable, checkable, and
 * edited one at a time like every other row in the schema. Sent and returned as this same shape;
 * the identity of the underlying row is not something a caller needs to track — a lot's whole
 * rate card is replaced together when it is edited.
 */
public record MrpRateBand(long minMrpPaise, Long maxMrpPaise, long costPaise) {

    public MrpRateBand {
        if (minMrpPaise < 0) {
            throw new IllegalArgumentException("minMrpPaise must not be negative, was " + minMrpPaise);
        }
        if (maxMrpPaise != null && maxMrpPaise <= minMrpPaise) {
            throw new IllegalArgumentException(
                    "maxMrpPaise (" + maxMrpPaise + ") must be above minMrpPaise (" + minMrpPaise + ")");
        }
        if (costPaise < 0) {
            throw new IllegalArgumentException("costPaise must not be negative, was " + costPaise);
        }
    }
}
