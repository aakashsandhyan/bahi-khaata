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

import java.util.UUID;

/**
 * One thing to look for inside a carton.
 *
 * <p>Named in the terms the screen shows it. {@code outstanding} is what the operator still has
 * to find, which is the only one of these numbers they need while working.
 *
 * @param needsMrp whether anyone has yet read the printed price off these goods. Asked once per
 *     product per delivery: the figure comes off the pack, and every pack in one delivery
 *     carries the same one, so asking again for each unit would be pure friction.
 * @param statedValuePaise what the supplier's sheet said this line is worth per unit — a
 *     marketplace selling price on a returns sheet, a supplier cost on a cost-plus one. Null
 *     where the sheet stated neither.
 * @param onlinePricePaise what the goods last sold for online, where that is known. Useful
 *     beside the MRP being typed: a printed price far from it is worth a second look.
 * @param indicativeCostPaise roughly what this will have cost per unit — the sheet's value
 *     scaled by the rate paid for the delivery. <strong>Not the final cost.</strong> That is
 *     settled only when the delivery closes and is spread across what actually arrived, so a
 *     line that turns up short ends up costing more per unit than this says.
 * @param recordedMrpPaise the MRP already read off these goods this delivery, where one has been,
 *     so the screen can show it back rather than ask again. Null until the first unit is priced;
 *     the inverse of {@code needsMrp} carrying the figure instead of just a flag.
 */
public record UnpackingLine(
        UUID lineId,
        String code,
        String name,
        long expected,
        long counted,
        long outstanding,
        boolean needsMrp,
        Long statedValuePaise,
        Long onlinePricePaise,
        Long indicativeCostPaise,
        Long recordedMrpPaise) {}
