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
 * What a price would be, and how it sits against the three figures that judge it.
 *
 * <p>Computed, not applied. The screen shows a table of these before anything is committed, so a
 * manager sees what a margin does to every line at once and which lines it makes trouble for.
 *
 * @param pricePaise the price this proposal would set
 * @param grossMarginPercent what the shop earns on it, as a percentage of the price
 * @param percentOfMrp where it sits under the legal ceiling, as a percentage of MRP
 * @param beatsOnline whether it undercuts the online price — the shop's promise; null when no
 *     online price is known to judge against
 * @param aboveMrp whether it breaches the legal ceiling, which the apply step refuses outright
 */
public record PriceProposal(
        UUID productId,
        long pricePaise,
        int grossMarginPercent,
        Integer percentOfMrp,
        Boolean beatsOnline,
        boolean aboveMrp) {}
