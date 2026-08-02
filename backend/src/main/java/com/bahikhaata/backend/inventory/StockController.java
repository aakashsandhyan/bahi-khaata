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
package com.bahikhaata.backend.inventory;

import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.BatchStockResponse;
import com.bahikhaata.contracts.StockOnHandResponse;
import com.bahikhaata.contracts.StockValuationResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stock questions: how much is on hand, what remains of each batch, what it is worth.
 *
 * <p>Every answer is derived from the ledger at the moment it is asked. An {@code asAt}
 * parameter reconstructs the position as it stood then, which is what makes a past figure
 * checkable rather than a matter of trusting a stored snapshot.
 */
@RestController
@RequestMapping("/api/stock/products/{productId}")
class StockController {

    private final StockLevels stock;
    private final ProductRepository products;

    StockController(StockLevels stock, ProductRepository products) {
        this.stock = stock;
        this.products = products;
    }

    @GetMapping
    ResponseEntity<StockOnHandResponse> onHand(
            @PathVariable UUID productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant asAt) {
        if (!products.existsById(productId)) {
            return ResponseEntity.notFound().build();
        }

        long quantity =
                asAt == null ? stock.onHand(productId) : stock.onHandAsAt(productId, asAt);

        return ResponseEntity.ok(
                new StockOnHandResponse(
                        productId.toString(), quantity, asAt == null ? null : asAt.toString()));
    }

    @GetMapping("/batches")
    ResponseEntity<List<BatchStockResponse>> batches(@PathVariable UUID productId) {
        if (!products.existsById(productId)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                stock.remainingByBatch(productId).stream()
                        .map(
                                held ->
                                        new BatchStockResponse(
                                                held.batch().getId().toString(),
                                                held.batch().getLot().getId().toString(),
                                                held.batch().getLot().getReceivedOn().toString(),
                                                held.quantityRemaining(),
                                                held.batch().getAllocatedUnitCost().paise(),
                                                held.batch().getCostBasis()))
                        .toList());
    }

    @GetMapping("/valuation")
    ResponseEntity<StockValuationResponse> valuation(
            @PathVariable UUID productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant asAt) {
        if (!products.existsById(productId)) {
            return ResponseEntity.notFound().build();
        }

        // Valuing "now" is valuing as at this instant; one code path, no special case.
        Instant at = asAt == null ? Instant.now() : asAt;

        return ResponseEntity.ok(
                new StockValuationResponse(
                        productId.toString(),
                        asAt == null ? null : asAt.toString(),
                        stock.valuationAsAt(productId, at).paise()));
    }
}
