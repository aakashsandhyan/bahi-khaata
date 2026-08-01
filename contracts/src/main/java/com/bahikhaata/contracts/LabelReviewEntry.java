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
 * One product's labels waiting for a reviewer, as a single row — the labels from a pricing command
 * reviewed in one go, not one row per sticker. Carries what the label will show (name, price, MRP)
 * and how many copies, plus the batch, category and on-hand count so the reviewer can edit the
 * product before sending it to the print queue.
 */
public record LabelReviewEntry(
        UUID jobId,
        UUID productId,
        UUID batchId,
        String barcode,
        String name,
        String categoryCode,
        long sellingPricePaise,
        Long mrpPaise,
        int copies,
        long onHand) {}
