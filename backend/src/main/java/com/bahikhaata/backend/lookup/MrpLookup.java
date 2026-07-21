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
package com.bahikhaata.backend.lookup;

import com.bahikhaata.contracts.Money;
import java.util.List;
import java.util.Map;

/**
 * Finding a printed retail price for goods whose pack nobody has read.
 *
 * <p>An assist and never a source of truth. Three rules hold whatever sits behind this:
 *
 * <ul>
 *   <li>It runs in the background and never blocks unpacking. Receiving goods cannot depend on
 *       the network — that is settled, and a shop with no internet must still be able to open
 *       cartons.
 *   <li>Anything it returns is an <em>estimate</em>. MRP is a legal figure printed on the pack,
 *       and a number fetched from a website is evidence about it, not the thing itself.
 *   <li>Someone holding the goods always overrides it.
 * </ul>
 *
 * <p>An interface because the source will change. It is a paid API today; it might be a
 * different one tomorrow, or nothing at all where a supplier gives a price list. What must not
 * change is that the rest of the system cannot tell the difference.
 */
public interface MrpLookup {

    /**
     * Looks up several products at once.
     *
     * <p>Batched because every provider charges or rate-limits per call, and one at a time is
     * how a backfill of two thousand products turns into an afternoon of retries.
     *
     * @param asins marketplace identifiers to look up
     * @return prices found, keyed by identifier. Absent means not found, which is ordinary and
     *     not an error — plenty of goods have no listing left.
     */
    Map<String, Money> lookup(List<String> asins);

    /** Whether this lookup can run at all. False when unconfigured, which is a normal state. */
    boolean isAvailable();

    /** What to tell a person when it is unavailable. */
    String unavailableReason();
}
