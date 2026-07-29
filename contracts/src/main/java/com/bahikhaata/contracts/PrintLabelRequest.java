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

import java.util.HashMap;
import java.util.Map;

public record PrintLabelRequest(
    String barcode,
    String productName,
    String category,
    String costPerUnit,
    String mrpPaise,
    String lotId,
    String expiryDate,
    String receivedDate) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("barcode", barcode);
        map.put("productName", productName);
        map.put("category", category);
        map.put("costPerUnit", costPerUnit);
        map.put("mrpPaise", mrpPaise);
        map.put("lotId", lotId);
        map.put("expiryDate", expiryDate);
        map.put("receivedDate", receivedDate);
        return map;
    }
}
