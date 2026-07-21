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
 * A supplier's consignment, parsed from their workbook and ready to record.
 *
 * <p>One category per lot, because that is how a consignment is priced: each category has its
 * own amount and its own basis, so each is costed separately.
 *
 * @param supplier who it came from
 * @param receivedOn the delivery date, ISO-8601
 * @param lots one per category
 */
public record ImportConsignmentRequest(String supplier, String receivedOn, List<ImportLot> lots) {}
