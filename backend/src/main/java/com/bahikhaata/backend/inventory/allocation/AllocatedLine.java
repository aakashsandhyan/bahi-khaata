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
package com.bahikhaata.backend.inventory.allocation;

import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.Money;

/**
 * What one line ended up costing.
 *
 * <p>The total is authoritative — line totals sum to the lot amount exactly. The unit cost is
 * derived from it and rounded down to the paise, so it cannot be multiplied back out to the
 * total without losing the remainder. Cost of goods sold uses the unit cost; reconciliation
 * uses the total.
 *
 * @param reference the caller's key, as supplied
 * @param allocatedTotal this line's share of the lot amount
 * @param allocatedUnitCost that share divided by the sellable quantity
 * @param basis how the figure was arrived at
 */
public record AllocatedLine(
        String reference, Money allocatedTotal, Money allocatedUnitCost, CostBasis basis) {}
