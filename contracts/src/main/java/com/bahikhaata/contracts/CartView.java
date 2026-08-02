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
 * The whole cart as the till shows it.
 *
 * @param savingPaise what the customer saves against MRP across the cart — the brand promise, a
 *     first-class figure, not a footnote
 * @param taxIsPlaceholder true while tax is a stand-in. The real figure is per-item by HSN code
 *     and needs the rates a CA supplies; until then this total is indicative and no invoice is
 *     issued from it
 */
public record CartView(
        UUID cartId,
        List<CartLineView> lines,
        long subtotalPaise,
        long taxPaise,
        long totalPaise,
        long savingPaise,
        boolean taxIsPlaceholder) {}
