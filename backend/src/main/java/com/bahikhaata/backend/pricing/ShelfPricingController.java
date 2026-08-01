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
    private final com.bahikhaata.backend.print.BulkLabelPrint labels;
    private final com.bahikhaata.backend.inventory.StockLevels stock;

    public ShelfPricingController(
            ShelfPricing shelfPricing,
            com.bahikhaata.backend.print.BulkLabelPrint labels,
            com.bahikhaata.backend.inventory.StockLevels stock) {
        this.shelfPricing = shelfPricing;
        this.labels = labels;
        this.stock = stock;
    }

    @GetMapping("/lots")
    public List<ShelfLot> lots() {
        return shelfPricing.lots();
    }

    @GetMapping("/lots/{lotId}/categories")
    public List<String> categoriesForLot(@PathVariable UUID lotId) {
        return shelfPricing.categoriesForLot(lotId);
    }

    // A list of zero or one: a hit is a single-element list, a miss an empty one. The client reads
    // it as a list (a scan that resolves nothing is "nothing here", not an error), so this stays a
    // 200 with [] rather than a 404.
    @GetMapping("/scan")
    public List<ScannedItem> scan(@RequestParam UUID lotId, @RequestParam String code) {
        return shelfPricing.resolveScanned(lotId, code).map(List::of).orElse(List.of());
    }

    // TRANSIENT — direct scan without choosing a lot first, for pricing already-manifested,
    // already-scanned stock in one scan. Zero-or-one, like /scan. Expected to be removed later.
    @GetMapping("/scan-any")
    public List<ScannedItem> scanAny(@RequestParam String code) {
        return shelfPricing.resolveScannedAnywhere(code).map(List::of).orElse(List.of());
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
        long before = stock.onHand(req.productId());
        ShelfPricedProduct saved = shelfPricing.saveExisting(req);
        // Pricing hands off to review: the product's labels wait as one entry for a reviewer to
        // send. Pass how much this command grew the stock, so the reviewer's count is kept and only
        // the newly-found units are added.
        labels.enqueueForReview(saved.productId(), stock.onHand(saved.productId()) - before);
        return saved;
    }

    @PostMapping("/manual")
    public ShelfPricedProduct saveManual(@RequestBody PriceManualRequest req) {
        ShelfPricedProduct saved = shelfPricing.saveManual(req);
        // A hand-keyed product is new, so all of its on-hand is newly added.
        labels.enqueueForReview(saved.productId(), stock.onHand(saved.productId()));
        return saved;
    }
}
