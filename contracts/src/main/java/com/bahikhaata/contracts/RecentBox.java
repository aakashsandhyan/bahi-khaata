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

import java.time.Instant;
import java.util.UUID;

/**
 * A box worked recently, for the rail that offers back the ones in hand.
 *
 * <p>Carries its tracking number so a tap reopens it, its category and progress so it reads at a
 * glance, and when it was last touched so the rail can say how long ago.
 */
public record RecentBox(
        UUID boxId,
        String trackingNumber,
        String categoryCode,
        long unitsExpected,
        long unitsCounted,
        long unitsUnlisted,
        boolean finished,
        Instant lastActivityAt) {}
