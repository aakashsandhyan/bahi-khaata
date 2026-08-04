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

import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Money;
import java.util.List;

/**
 * The result of spreading a lot's cost across its lines.
 *
 * @param lines what each line costs, in the order supplied
 * @param method how the figures were produced, recorded so a cost can be judged later
 * @param totalAllocated the sum of the line totals, which equals the lot amount
 */
public record Allocation(List<AllocatedLine> lines, AllocationMethod method, Money totalAllocated) {}
