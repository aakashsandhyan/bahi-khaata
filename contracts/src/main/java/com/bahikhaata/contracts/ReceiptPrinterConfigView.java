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

/**
 * The receipt printer's configuration, read and written by the admin screen. {@code transport} is
 * "LAN" (address is host:port, raw-9100) or "USB" (address is the OS printer name).
 */
public record ReceiptPrinterConfigView(
        String address,
        String transport,
        boolean enabled,
        String testStatus,
        String testError,
        Instant lastTestedAt) {}
