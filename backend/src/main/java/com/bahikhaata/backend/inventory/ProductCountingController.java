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

import com.bahikhaata.contracts.ProductCountRequest;
import com.bahikhaata.contracts.ProductCountResult;
import com.bahikhaata.contracts.ProductLotLines;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Counting a product across a delivery's boxes in one act — reached after a lot and a product are
 * chosen in the catalogue.
 */
@RestController
@RequestMapping("/api/product-counting")
class ProductCountingController {

    private final ProductCounting counting;

    ProductCountingController(ProductCounting counting) {
        this.counting = counting;
    }

    /** A product's outstanding box-lines in an open lot — the grid to count against. */
    @GetMapping("/lots/{lotId}/products/{productId}/lines")
    ProductLotLines lines(@PathVariable UUID lotId, @PathVariable UUID productId) {
        return counting.linesFor(lotId, productId);
    }

    /** Records the per-box quantities in one act; refused entries come back for re-entry. */
    @PostMapping("/count")
    ProductCountResult count(@RequestBody ProductCountRequest request) {
        return counting.count(request);
    }
}
