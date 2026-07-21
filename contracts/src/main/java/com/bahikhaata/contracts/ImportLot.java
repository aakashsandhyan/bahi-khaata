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
 * One category of a consignment: what was paid for it, and what it contains.
 *
 * @param categoryCode our category for these goods
 * @param amountPaidPaise what was paid for this category
 * @param allocationMethod how its per-product costs are arrived at
 * @param lines the products in it
 */
public record ImportLot(
        String categoryCode,
        long amountPaidPaise,
        AllocationMethod allocationMethod,
        List<ImportLine> lines) {}
