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
 * Queues a self-contained label print job: the request carries the label fields directly, so the
 * executor renders without a database read. {@code mrpPaise} is null when there is no confirmed
 * MRP. {@code productId} is optional — supplied so the printed product can be marked labelled,
 * never read to render.
 */
public record QueuePrintJobRequest(
    String barcode,
    String productName,
    long sellingPricePaise,
    Long mrpPaise,
    int copies,
    UUID productId) {}
