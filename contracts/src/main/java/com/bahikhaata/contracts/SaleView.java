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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A completed sale as the till and sales screen show it. {@code printFailed} is true when the sale
 * was recorded but its bill did not print — the operator can reprint it.
 */
public record SaleView(
        UUID saleId,
        long billNo,
        String billNoFormatted,
        PaymentMethod paymentMethod,
        long subtotalPaise,
        long savingPaise,
        long taxPaise,
        long totalPaise,
        String operatorName,
        Instant createdAt,
        List<SaleLineView> lines,
        boolean printFailed) {}
