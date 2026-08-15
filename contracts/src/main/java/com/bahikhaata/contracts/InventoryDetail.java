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
package com.bahikhaata.contracts;

import java.util.List;
import java.util.UUID;

/**
 * One product's full story: batches, ledger movements, and price history composed into a single
 * payload (design decision D3 of palletworks-inventory) — the Item detail view's one read.
 *
 * <p>Cost basis, price, and margin here are the product's headline (GOOD-condition) figures; the
 * per-batch list beneath carries every condition's own cost and bin individually.
 *
 * @param barcodes every code that resolves to this product (manufacturer, internal/shelf,
 *     marketplace, unit label)
 * @param costBasisPaise the on-hand-weighted unit cost of GOOD-condition stock, or null when none
 *     is costed
 * @param sellingPricePaise the product's ordinary selling price, or null if unpriced
 * @param marginPercent derived from price and cost basis, or null when either is unknown
 * @param receivedUnits total units this product has ever received (PURCHASE_RECEIPT)
 * @param soldUnits total units this product has ever sold (SALE)
 * @param batches every batch across every lot and condition, newest delivery first
 * @param movements the stock ledger, newest first
 * @param priceHistory every recorded price change, newest first
 */
public record InventoryDetail(
        UUID productId,
        String productName,
        String categoryCode,
        List<String> barcodes,
        Long costBasisPaise,
        Long sellingPricePaise,
        Integer marginPercent,
        long receivedUnits,
        long soldUnits,
        List<InventoryBatchLine> batches,
        List<InventoryMovement> movements,
        List<PriceChange> priceHistory) {}
