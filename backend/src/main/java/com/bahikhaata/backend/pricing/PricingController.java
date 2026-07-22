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
package com.bahikhaata.backend.pricing;

import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.PriceProposal;
import com.bahikhaata.contracts.PriceableItem;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pricing goods for the shelf.
 *
 * <p>Under {@code /api/admin} deliberately. This is where cost and margin are seen and set, and
 * an operator must see neither — so when roles arrive, the gate is this one path prefix rather
 * than a scattering of endpoints to remember. Until then it is open, which is a known gap and
 * not a hidden one: the dashboard that calls this is an admin tool run on an admin machine.
 */
@RestController
@RequestMapping("/api/admin/pricing")
class PricingController {

    private final PricingWorkbench workbench;

    PricingController(PricingWorkbench workbench) {
        this.workbench = workbench;
    }

    /** Goods ready to be priced, optionally within one category. */
    @GetMapping("/priceable")
    List<PriceableItem> priceable(@RequestParam(required = false) String category) {
        return workbench.priceable(category);
    }

    /** What a target margin would make of a category, committing nothing. */
    @GetMapping("/preview")
    List<PriceProposal> preview(
            @RequestParam String category, @RequestParam int marginPercent) {
        return workbench.previewCategory(category, marginPercent);
    }

    /** Sets one product's price. */
    @PostMapping("/products/{productId}")
    ResponseEntity<Void> setPrice(
            @PathVariable UUID productId, @RequestBody SetPriceRequest request) {
        workbench.apply(productId, Money.ofPaise(request.pricePaise()));
        return ResponseEntity.noContent().build();
    }

    /** Prices every still-unpriced product in a category at a target margin. */
    @PostMapping("/category/{category}")
    PricingWorkbench.BulkResult priceCategory(
            @PathVariable String category, @RequestParam int marginPercent) {
        return workbench.applyCategory(category, marginPercent);
    }

    /** A price refused — below-nothing, or above the MRP — is the caller's to fix. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    /** Pricing before the cost is known is a sequencing mistake, reported as a conflict. */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<String> conflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(e.getMessage());
    }

    record SetPriceRequest(long pricePaise) {}
}
