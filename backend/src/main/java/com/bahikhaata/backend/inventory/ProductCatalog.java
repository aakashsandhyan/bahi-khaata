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
package com.bahikhaata.backend.inventory;

import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.CatalogDetail;
import com.bahikhaata.contracts.CatalogEntry;
import com.bahikhaata.contracts.CatalogStatus;
import com.bahikhaata.contracts.Origin;
import com.bahikhaata.contracts.ProductCode;
import com.bahikhaata.contracts.ProductStates;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Browsing the shop's products and opening one to its detail — the shared product finder.
 *
 * <p>Every product wears a {@link CatalogStatus}: {@link CatalogStatus#FOUND} once it has a counted
 * batch or a scannable code, {@link CatalogStatus#ON_PAPER} while it is only a manifest line nobody
 * has laid hands on. The on-paper ones are the goods a delivery still owes, so they are the default
 * view. Status is decided in the database (see {@link ProductCatalogRepository}) so the filter and
 * the paging hold together.
 *
 * <p>Lives in the inventory package, not catalog, because deciding "found" reads a {@link Batch}
 * alongside a {@code Barcode}, and the dependency only runs inventory → catalog. It reuses
 * {@link GoodsRemediation#statesOf} for the detail's stock states rather than compute them afresh.
 */
@Service
public class ProductCatalog {

    /** Kept modest so a page is a few database reads, never one query per row. */
    private static final int MAX_PAGE = 100;

    private final ProductCatalogRepository catalog;
    private final ProductRepository products;
    private final BatchRepository batches;
    private final BarcodeRepository barcodes;
    private final GoodsRemediation remediation;

    ProductCatalog(
            ProductCatalogRepository catalog,
            ProductRepository products,
            BatchRepository batches,
            BarcodeRepository barcodes,
            GoodsRemediation remediation) {
        this.catalog = catalog;
        this.products = products;
        this.batches = batches;
        this.barcodes = barcodes;
        this.remediation = remediation;
    }

    /**
     * A page of the catalogue, name-filtered and by status.
     *
     * @param q name fragment to match, or null/blank for the whole catalogue
     * @param status one of {@code on-paper} (the default), {@code found}, or {@code all}
     */
    @Transactional(readOnly = true)
    public List<CatalogEntry> browse(String q, String status, int page, int size) {
        String needle = q == null ? "" : q;
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        String mode = status == null ? "on-paper" : status;

        return switch (mode) {
            case "found" -> catalog.findFound(needle, Origin.MARKETPLACE, pageable).stream()
                    .map(p -> entry(p, CatalogStatus.FOUND))
                    .toList();
            case "all" -> markMixed(catalog.findByName(needle, pageable));
            default -> catalog.findOnPaper(needle, Origin.MARKETPLACE, pageable).stream()
                    .map(p -> entry(p, CatalogStatus.ON_PAPER))
                    .toList();
        };
    }

    /** For an {@code all} page, mark each row found or on-paper with two bulk reads, not one per row. */
    private List<CatalogEntry> markMixed(List<Product> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = page.stream().map(Product::getId).toList();
        Set<UUID> found = new java.util.HashSet<>(catalog.foundByBatch(ids));
        found.addAll(catalog.foundByCode(ids, Origin.MARKETPLACE));
        return page.stream()
                .map(p -> entry(p, found.contains(p.getId())
                        ? CatalogStatus.FOUND
                        : CatalogStatus.ON_PAPER))
                .toList();
    }

    private CatalogEntry entry(Product p, CatalogStatus status) {
        return new CatalogEntry(
                p.getId(), p.getName(), p.getCategory().code(), status, p.isPriced());
    }

    /**
     * One product opened: its stock states (reused from remediation), its codes, and its standing.
     */
    @Transactional(readOnly = true)
    public CatalogDetail detail(UUID productId) {
        Product product =
                products.findById(productId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("no such product: " + productId));
        ProductStates states = remediation.statesOf(productId);
        List<ProductCode> codes =
                barcodes.findByProductId(productId).stream()
                        .map(b -> new ProductCode(b.getCode(), b.getOrigin()))
                        .toList();
        boolean found =
                !batches.findByProductId(productId).isEmpty()
                        || codes.stream().anyMatch(c -> c.origin() != Origin.MARKETPLACE);
        return new CatalogDetail(
                states,
                codes,
                found ? CatalogStatus.FOUND : CatalogStatus.ON_PAPER,
                product.isPriced());
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 25;
        }
        return Math.min(size, MAX_PAGE);
    }
}
