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
package com.bahikhaata.backend.catalog;

import com.bahikhaata.backend.inventory.CategoryCatalog;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CreateProductRequest;
import com.bahikhaata.backend.shelf.ProductPricing;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.ProductResponse;
import com.bahikhaata.contracts.SetCategoryRequest;
import com.bahikhaata.contracts.SetPriceRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogue endpoints: resolve a scanned code, create a product, set a price.
 *
 * <p>No terminal screen uses these yet — products are created through goods-in, which arrives
 * with lots. They exist so the catalogue is reachable over the boundary the terminal will use,
 * rather than only from inside the backend.
 */
@RestController
@RequestMapping("/api/products")
class ProductController {

    private final ProductRepository products;
    private final BarcodeResolver resolver;
    private final ProductPricing pricing;
    private final CategoryCatalog categoryCatalog;

    ProductController(
            ProductRepository products,
            BarcodeResolver resolver,
            ProductPricing pricing,
            CategoryCatalog categoryCatalog) {
        this.products = products;
        this.resolver = resolver;
        this.pricing = pricing;
        this.categoryCatalog = categoryCatalog;
    }

    /**
     * The product a scanned code identifies.
     *
     * <p>An unrecognised code is a 404, not an empty 200: the caller must distinguish "no such
     * code" from "a product with nothing in it", and must never be able to treat an unknown
     * scan as a sellable line.
     */
    @GetMapping("/by-barcode/{code}")
    ResponseEntity<ProductResponse> byBarcode(@PathVariable String code) {
        return resolver.resolve(code)
                .map(product -> ResponseEntity.ok(ProductResponses.of(product)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Creates a product. It is created unpriced; a price is a separate, deliberate act. */
    @PostMapping
    ResponseEntity<ProductResponse> create(@RequestBody CreateProductRequest request) {
        Product saved =
                products.save(
                        new Product(
                                request.name(),
                                Category.of(request.category()),
                                request.hsnCode(),
                                request.attributes()));

        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponses.of(saved));
    }

    /**
     * Sets a product's selling price.
     *
     * <p>Routed through {@link ProductPricing} rather than setting it here, because a price
     * depends on things this package cannot see: whether the delivery it came from has been
     * closed, so its cost is known, and what MRP is printed on the goods now going out.
     *
     * <p>A non-positive amount, an unknown cost, or a price above the printed MRP are all
     * refused as bad requests.
     */
    @PutMapping("/{id}/price")
    ResponseEntity<ProductResponse> setPrice(
            @PathVariable UUID id, @RequestBody SetPriceRequest request) {
        if (products.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                ProductResponses.of(
                        pricing.setSellingPrice(id, Money.ofPaise(request.sellingPricePaise()))));
    }

    /**
     * Reclassifies a product's category (department).
     *
     * <p>A plain reclassification (design decision D4 of palletworks-nav): it neither sets nor
     * changes a price, and moves no stock, so it does not go through {@link ProductPricing} or
     * the shelf-pricing save path — those demand a batch and reprice as a side effect, which this
     * is not. {@code Product.setCategory} is the only thing it touches.
     *
     * <p>{@code Category.of} accepts any non-blank string; it does not check the code against
     * the shop's actual category list. That check happens here, against {@link CategoryCatalog},
     * so an unknown category is refused as a 400 rather than surfacing as a 500 when the
     * database's foreign key on {@code product.category} rejects the write.
     */
    @PatchMapping("/{id}/category")
    ResponseEntity<ProductResponse> setCategory(
            @PathVariable UUID id, @RequestBody SetCategoryRequest request) {
        return products
                .findById(id)
                .map(
                        product -> {
                            if (!categoryCatalog.allCodes().contains(request.categoryCode())) {
                                throw new IllegalArgumentException(
                                        "no such category: " + request.categoryCode());
                            }
                            product.setCategory(Category.of(request.categoryCode()));
                            return ResponseEntity.ok(ProductResponses.of(products.save(product)));
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Pricing goods whose cost is not yet settled is a sequencing mistake, not a bad value:
     * the caller has to finish unpacking first, which the message says.
     */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<String> notYet(IllegalStateException e) {
        return ResponseEntity.status(409).body(e.getMessage());
    }

    /**
     * A rejected price is the caller's mistake, not a server fault. Product enforces that a
     * price is positive; surfacing it as 400 keeps that rule visible at the boundary.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
