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
import java.util.Map;
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
     * A page of the catalogue, narrowed by name, status, and category together — each filter that is
     * set narrows the list further.
     *
     * @param q name fragment to match, or null/blank for any name
     * @param status one of {@code on-paper} (the default), {@code found}, or {@code all}
     * @param category a category code to restrict to, or null/blank for every category
     * @param lot a lot (delivery) to scope to, or null/blank for every delivery. When set, the list is
     *     restricted to products expected in that lot, and units and found/on-paper are that lot's.
     */
    @Transactional(readOnly = true)
    public List<CatalogEntry> browse(
            String q, String status, String category, String lot, int page, int size) {
        String needle = q == null ? "" : q;
        String cat = category == null ? "" : category;
        UUID lotId = (lot == null || lot.isBlank()) ? null : UUID.fromString(lot);
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        String mode = status == null ? "on-paper" : status;

        List<Product> rows;
        if (lotId == null) {
            rows = switch (mode) {
                case "found" -> catalog.findFound(needle, cat, Origin.MARKETPLACE, pageable);
                case "all" -> catalog.findByName(needle, cat, pageable);
                default -> catalog.findOnPaper(needle, cat, Origin.MARKETPLACE, pageable);
            };
        } else {
            rows = switch (mode) {
                case "found" -> catalog.findFoundInLot(needle, cat, lotId, pageable);
                case "all" -> catalog.findByNameInLot(needle, cat, lotId, pageable);
                default -> catalog.findOnPaperInLot(needle, cat, lotId, pageable);
            };
        }
        return toEntries(rows, mode, lotId);
    }

    /**
     * Builds the rows, attaching each product's found/on-paper status and its expected/counted totals.
     * When a lot is given, both the status and the totals are scoped to that lot.
     */
    private List<CatalogEntry> toEntries(List<Product> rows, String mode, UUID lotId) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(Product::getId).toList();
        Map<UUID, long[]> totals = lotId == null ? totalsFor(ids) : totalsForLot(ids, lotId);
        // Status is uniform for the found/on-paper filters; only "all" is mixed and needs the reads.
        Set<UUID> found =
                "all".equals(mode)
                        ? (lotId == null ? foundSet(ids) : foundSetInLot(ids, lotId))
                        : Set.of();
        return rows.stream()
                .map(p -> {
                    CatalogStatus status = switch (mode) {
                        case "found" -> CatalogStatus.FOUND;
                        case "all" -> found.contains(p.getId())
                                ? CatalogStatus.FOUND
                                : CatalogStatus.ON_PAPER;
                        default -> CatalogStatus.ON_PAPER;
                    };
                    long[] t = totals.getOrDefault(p.getId(), ZERO);
                    return new CatalogEntry(
                            p.getId(), p.getName(), p.getCategory().code(), status,
                            p.isPriced(), t[0], t[1]);
                })
                .toList();
    }

    /** The products, among the given, that have a batch or a physical code — the "all"-page status read. */
    private Set<UUID> foundSet(List<UUID> ids) {
        Set<UUID> found = new java.util.HashSet<>(catalog.foundByBatch(ids));
        found.addAll(catalog.foundByCode(ids, Origin.MARKETPLACE));
        return found;
    }

    /** The lot-scoped "all"-page status read: found means a batch counted in this lot. */
    private Set<UUID> foundSetInLot(List<UUID> ids, UUID lotId) {
        return catalog.foundByBatchInLot(ids, lotId);
    }

    /** Per-product summed expected and counted units, from one bulk read; {expected, counted}. */
    private Map<UUID, long[]> totalsFor(List<UUID> ids) {
        return toTotalsMap(catalog.expectedTotals(ids));
    }

    /** The same, summed over one lot only. */
    private Map<UUID, long[]> totalsForLot(List<UUID> ids, UUID lotId) {
        return toTotalsMap(catalog.expectedTotalsInLot(ids, lotId));
    }

    private Map<UUID, long[]> toTotalsMap(List<Object[]> rows) {
        Map<UUID, long[]> map = new java.util.HashMap<>();
        for (Object[] row : rows) {
            map.put(
                    (UUID) row[0],
                    new long[] {((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return map;
    }

    private static final long[] ZERO = {0L, 0L};

    /**
     * One product opened: its stock states (reused from remediation), its codes, its standing, and
     * how much of it the manifest expects versus has been counted across every box.
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
        long[] t = totalsFor(List.of(productId)).getOrDefault(productId, ZERO);
        return new CatalogDetail(
                states,
                codes,
                found ? CatalogStatus.FOUND : CatalogStatus.ON_PAPER,
                product.isPriced(),
                t[0],
                t[1]);
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 25;
        }
        return Math.min(size, MAX_PAGE);
    }
}
