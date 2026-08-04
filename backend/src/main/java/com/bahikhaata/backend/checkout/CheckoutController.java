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
import com.bahikhaata.contracts.CartView;
import com.bahikhaata.contracts.SaleView;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The till. Scan to add, adjust quantities, clear — the cart before it becomes a sale.
 *
 * <p>Errors come back as a plain sentence for the person at the counter, inline rather than as
 * something that blocks the screen: an unsellable scan, an unknown code, a sale already closed.
 */
@RestController
@RequestMapping("/api/checkout")
class CheckoutController {

    private final Checkout checkout;
    private final ReceiptPrinting receiptPrinting;

    CheckoutController(Checkout checkout, ReceiptPrinting receiptPrinting) {
        this.checkout = checkout;
        this.receiptPrinting = receiptPrinting;
    }

    /** Starts a fresh sale. */
    @PostMapping("/cart")
    CartView open() {
        return checkout.open();
    }

    @GetMapping("/cart/{cartId}")
    CartView view(@PathVariable UUID cartId) {
        return checkout.view(cartId);
    }

    /** Adds what was scanned, or raises its quantity. */
    @PostMapping("/cart/{cartId}/scan")
    CartView scan(@PathVariable UUID cartId, @RequestBody ScanRequest request) {
        return checkout.scan(cartId, request.code());
    }

    @PostMapping("/cart/{cartId}/lines/{lineId}/quantity")
    CartView setQuantity(
            @PathVariable UUID cartId,
            @PathVariable UUID lineId,
            @RequestBody QuantityRequest request) {
        return checkout.setQuantity(cartId, lineId, request.quantity());
    }

    @DeleteMapping("/cart/{cartId}/lines/{lineId}")
    CartView removeLine(@PathVariable UUID cartId, @PathVariable UUID lineId) {
        return checkout.removeLine(cartId, lineId);
    }

    @PostMapping("/cart/{cartId}/clear")
    CartView clear(@PathVariable UUID cartId) {
        return checkout.clear(cartId);
    }

    /** Completes the cart into a sale and returns the recorded bill. */
    @PostMapping("/cart/{cartId}/complete")
    SaleView complete(
            @PathVariable UUID cartId,
            @RequestBody com.bahikhaata.contracts.CompleteSaleRequest request) {
        // complete() opens its own transaction and commits the sale + ledger before returning; the
        // bill is only printed afterwards, so a jammed or offline printer can never roll it back — a
        // print failure is flagged as printFailed and the operator reprints from the stored sale.
        Sale sale = checkout.complete(cartId, request.paymentMethod(), request.operatorName());
        boolean printFailed = receiptPrinting.printBill(checkout.toView(sale, false));
        return checkout.toView(sale, printFailed);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<String> conflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(e.getMessage());
    }

    record ScanRequest(String code) {}

    record QuantityRequest(long quantity) {}
}
