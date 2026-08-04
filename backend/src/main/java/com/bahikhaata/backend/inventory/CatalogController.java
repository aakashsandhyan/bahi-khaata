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

import com.bahikhaata.contracts.CatalogDetail;
import com.bahikhaata.contracts.CatalogEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The product catalogue — browsing by name and found status, and opening a product to its detail.
 *
 * <p>The URL reads by domain ({@code /api/catalog}) though the code sits with inventory, because
 * deciding whether a product has been found reads a batch as well as a barcode.
 */
@RestController
@RequestMapping("/api/catalog")
class CatalogController {

    private final ProductCatalog catalog;

    CatalogController(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * A page of the catalogue.
     *
     * @param q name fragment, or blank for the whole catalogue
     * @param status {@code on-paper} (default — the goods still unfound), {@code found}, or {@code all}
     * @param category a category code to restrict to, or blank for every category
     * @param lot a lot (delivery) to scope to, or blank for every delivery
     */
    @GetMapping
    List<CatalogEntry> browse(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "on-paper") String status,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String lot,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return catalog.browse(q, status, category, lot, page, size);
    }

    /** One product opened: its stock states, its codes, and whether it is found and priced. */
    @GetMapping("/products/{id}")
    CatalogDetail detail(@PathVariable UUID id) {
        return catalog.detail(id);
    }
}
