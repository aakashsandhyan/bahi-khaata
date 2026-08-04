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
package com.bahikhaata.backend.checkout;

import com.bahikhaata.backend.print.ReceiptPrinting;
import com.bahikhaata.contracts.SaleSummary;
import com.bahikhaata.contracts.SaleView;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The record of past sales: a recent list, one sale by its bill number, and a reprint. A reprint
 * always re-renders from the stored, immutable sale — never a cart — so an old bill comes out the
 * same however prices have moved since.
 */
@RestController
@RequestMapping("/api/sales")
class SalesController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final Checkout checkout;
    private final ReceiptPrinting receiptPrinting;

    SalesController(Checkout checkout, ReceiptPrinting receiptPrinting) {
        this.checkout = checkout;
        this.receiptPrinting = receiptPrinting;
    }

    @GetMapping
    List<SaleSummary> recent(@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return checkout.recentSales(Math.min(Math.max(limit, 1), MAX_LIMIT));
    }

    @GetMapping("/{billNo}")
    SaleView byBillNo(@PathVariable long billNo) {
        return checkout.saleByBillNo(billNo);
    }

    /** Reprints a stored sale. Like completion, a print failure is flagged, never an error. */
    @PostMapping("/{saleId}/reprint")
    SaleView reprint(@PathVariable UUID saleId) {
        SaleView view = checkout.toView(checkout.requireSale(saleId), false);
        boolean printFailed = receiptPrinting.printBill(view);
        return checkout.toView(checkout.requireSale(saleId), printFailed);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> notFound(IllegalArgumentException e) {
        return ResponseEntity.status(404).body(e.getMessage());
    }
}
