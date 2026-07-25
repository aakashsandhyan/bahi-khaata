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
package com.bahikhaata.backend.lookup;

import com.bahikhaata.backend.catalog.Barcode;
import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.Origin;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database half of the MRP fill, kept apart from the look-up on purpose.
 *
 * <p>SQLite runs on a single connection (pool of one), so anything that holds a transaction open
 * holds the whole shop: a scan being counted, a sale being rung, all wait behind it. The look-up is
 * slow — several seconds per page over the network — so it must never run inside a transaction.
 *
 * <p>So the network work lives in {@link MrpBackfill}, with no transaction at all, and every touch
 * of the database lives here in short transactions of its own. Because this is a separate bean, a
 * call from the orchestrator crosses a proxy and each method really is its own committed unit — the
 * connection is taken, the rows are written, and it is handed straight back.
 */
@Service
class MrpApplier {

    private static final Logger log = LoggerFactory.getLogger(MrpApplier.class);

    private final BatchRepository batches;
    private final BarcodeRepository barcodes;

    MrpApplier(BatchRepository batches, BarcodeRepository barcodes) {
        this.batches = batches;
        this.barcodes = barcodes;
    }

    /**
     * Every distinct marketplace reference held with no price yet whose product has not been tried —
     * the whole work list for a background fill, snapshotted in one read.
     */
    @Transactional(readOnly = true)
    List<String> waitingUntried() {
        return collect(Integer.MAX_VALUE);
    }

    /** The same, capped, so a bounded first run can be tried small before the source is trusted. */
    @Transactional(readOnly = true)
    List<String> waiting(int limit) {
        return collect(limit);
    }

    private List<String> collect(int limit) {
        LinkedHashSet<String> asins = new LinkedHashSet<>();
        for (Batch batch : batches.findAll()) {
            if (asins.size() >= limit) {
                break;
            }
            if (batch.getMrp() == null
                    && batch.getQuantityReceived() > 0
                    && !batch.getProduct().isMrpLookupAttempted()) {
                marketplaceCodeOf(batch.getProduct().getId()).ifPresent(asins::add);
            }
        }
        return List.copyOf(asins);
    }

    /**
     * Records found prices and marks every product in the chunk as tried, found or not, so no later
     * run asks the same one again. Only still-unpriced held batches are written, so this is safe to
     * run over items another pass already filled. Runs in its own short transaction.
     */
    @Transactional
    Applied apply(List<String> asins, Map<String, Money> found) {
        Instant at = Instant.now();
        int recorded = 0;
        int refused = 0;
        for (String asin : asins) {
            Product product =
                    barcodes.findByCode(asin).map(Barcode::getProduct).orElse(null);
            if (product == null) {
                continue;
            }
            // Tried, whether or not a price came back — never ask this one again.
            product.markMrpLookupAttempted(at);
            Money price = found.get(asin);
            if (price == null) {
                continue;
            }
            for (Batch batch : batches.findByProductId(product.getId())) {
                if (batch.getMrp() == null && batch.getQuantityReceived() > 0) {
                    try {
                        // An estimate, and held to the same plausibility checks as a hand-typed
                        // figure — a look-up is no more trustworthy than a person.
                        batch.recordMrp(price, true);
                        recorded++;
                    } catch (RuntimeException e) {
                        refused++;
                        log.info("Refused a looked-up price for {}: {}", asin, e.getMessage());
                    }
                }
            }
        }
        return new Applied(recorded, refused);
    }

    /**
     * Clears the tried-mark for a set of references — used when a run stops because the source was
     * blocking, so a source that never really answered leaves nothing burned behind it.
     */
    @Transactional
    void forget(List<String> asins) {
        for (String asin : asins) {
            barcodes.findByCode(asin)
                    .map(Barcode::getProduct)
                    .ifPresent(Product::clearMrpLookupAttempted);
        }
    }

    /** The supplier's marketplace reference for a product, which is what a look-up takes. */
    @Transactional(readOnly = true)
    Optional<String> marketplaceCode(UUID productId) {
        return marketplaceCodeOf(productId);
    }

    private Optional<String> marketplaceCodeOf(UUID productId) {
        return barcodes.findByProductId(productId).stream()
                .filter(barcode -> barcode.getOrigin() == Origin.MARKETPLACE)
                .map(Barcode::getCode)
                .findFirst();
    }

    /** What one {@link #apply} did: prices recorded, and prices returned but rejected as implausible. */
    record Applied(int recorded, int refused) {}
}
