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

import com.bahikhaata.contracts.PriceExistingRequest;
import com.bahikhaata.contracts.PriceManualRequest;
import com.bahikhaata.contracts.PriceSuggestion;
import com.bahikhaata.contracts.ScannedItem;
import com.bahikhaata.contracts.ShelfLot;
import com.bahikhaata.contracts.ShelfPricedProduct;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The lot-first pricing workbench API: list open lots, resolve a scanned item, offer the lot's
 * categories and a margin-suggested price, and save a priced product by either path.
 */
@RestController
@RequestMapping("/api/pricing/shelf")
public class ShelfPricingController {

    private final ShelfPricing shelfPricing;

    public ShelfPricingController(ShelfPricing shelfPricing) {
        this.shelfPricing = shelfPricing;
    }

    @GetMapping("/lots")
    public List<ShelfLot> lots() {
        return shelfPricing.lots();
    }

    @GetMapping("/lots/{lotId}/categories")
    public List<String> categoriesForLot(@PathVariable UUID lotId) {
        return shelfPricing.categoriesForLot(lotId);
    }

    @GetMapping("/scan")
    public ResponseEntity<ScannedItem> scan(
            @RequestParam UUID lotId, @RequestParam String code) {
        return shelfPricing.resolveScanned(lotId, code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/suggest")
    public PriceSuggestion suggest(
            @RequestParam long unitCostPaise,
            @RequestParam String categoryCode,
            @RequestParam(required = false) Integer marginPercent) {
        return shelfPricing.suggestPrice(unitCostPaise, categoryCode, marginPercent);
    }

    @PostMapping("/existing")
    public ShelfPricedProduct saveExisting(@RequestBody PriceExistingRequest req) {
        return shelfPricing.saveExisting(req);
    }

    @PostMapping("/manual")
    public ShelfPricedProduct saveManual(@RequestBody PriceManualRequest req) {
        return shelfPricing.saveManual(req);
    }
}
