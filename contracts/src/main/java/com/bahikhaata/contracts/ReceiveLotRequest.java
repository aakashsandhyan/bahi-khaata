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

import java.util.List;

/**
 * A delivery being received: what was paid, and what arrived.
 *
 * <p>No per-product cost is supplied except where the supplier itemised one — costs are
 * derived by allocating the lot amount, which is what keeps them reconciling back to what was
 * actually paid.
 *
 * @param supplier who it came from
 * @param receivedOn the delivery date, ISO-8601. FIFO consumes by this, so a late-entered
 *     delivery still consumes in true arrival order
 * @param amountPaidPaise what was paid for the lot
 * @param freightPaise what it cost to get here — allocated too, being part of landed cost
 * @param lines what arrived
 */
public record ReceiveLotRequest(
        String supplier,
        String receivedOn,
        long amountPaidPaise,
        long freightPaise,
        List<ReceiveLotLine> lines) {}
