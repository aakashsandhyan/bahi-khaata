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
package com.bahikhaata.backend.shelf;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.contracts.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What must be true before goods may be sold, and what is still missing.
 *
 * <p>Three things: a selling price, an MRP read off the goods, and a printed label. The label
 * is the gate — it is the last checkable step between a carton and the shelf, and the one a
 * customer actually sees.
 *
 * <p>Reporting <em>which</em> of the three is missing matters more than the yes or no. Someone
 * holding an item that will not scan through needs to know whether to price it, read a figure
 * off the pack, or run the labels — and "not sellable" tells them none of that.
 */
@Service
public class ShelfReadiness {

    private final BatchRepository batches;

    ShelfReadiness(BatchRepository batches) {
        this.batches = batches;
    }

    @Transactional(readOnly = true)
    public Readiness of(UUID batchId) {
        Batch batch =
                batches.findById(batchId)
                        .orElseThrow(() -> new IllegalArgumentException("no such batch: " + batchId));
        return readinessOf(batch);
    }

    /**
     * Prints labels for a batch.
     *
     * <p>Refused unless it is otherwise ready, because a label that cannot show a price or a
     * saving is not a label — and printing one anyway would put goods on the floor bearing an
     * incomplete tag, which is the exact failure the gate exists to prevent.
     */
    @Transactional
    public LabelContent label(UUID batchId, Instant at) {
        Batch batch =
                batches.findById(batchId)
                        .orElseThrow(() -> new IllegalArgumentException("no such batch: " + batchId));
        Readiness readiness = readinessOf(batch);
        if (!readiness.missingBeforeLabelling().isEmpty()) {
            throw new IllegalStateException(
                    "cannot label batch "
                            + batchId
                            + ": "
                            + String.join(", ", readiness.missingBeforeLabelling()));
        }
        batch.markLabelled(at);
        return contentFor(batch);
    }

    /** What a label says, without touching a printer. */
    @Transactional(readOnly = true)
    public LabelContent contentOf(UUID batchId) {
        Batch batch =
                batches.findById(batchId)
                        .orElseThrow(() -> new IllegalArgumentException("no such batch: " + batchId));
        if (batch.getMrp() == null) {
            throw new IllegalStateException(
                    "batch " + batchId + " has no recorded MRP, so a label has no saving to show");
        }
        if (batch.getProduct().getSellingPrice() == null) {
            throw new IllegalStateException(
                    "batch " + batchId + " belongs to an unpriced product, so a label has no"
                            + " price to show");
        }
        return contentFor(batch);
    }

    private Readiness readinessOf(Batch batch) {
        Product product = batch.getProduct();
        boolean priced = product.getSellingPrice() != null;
        boolean hasMrp = batch.getMrp() != null;
        boolean labelled = batch.isLabelled();
        return new Readiness(batch.getId(), priced, hasMrp, labelled);
    }

    private LabelContent contentFor(Batch batch) {
        Money mrp = batch.getMrp();
        Money price = batch.getProduct().getSellingPrice();
        long saving = mrp.paise() - price.paise();
        // Rounded to a whole percent, downward, so the figure on the tag is never larger than
        // the saving actually given.
        long percent = mrp.paise() == 0 ? 0 : (saving * 100) / mrp.paise();
        return new LabelContent(
                batch.getId(),
                batch.getProduct().getName(),
                mrp,
                price,
                Money.ofPaise(saving),
                percent,
                batch.isMrpEstimate());
    }

    /**
     * Whether goods may go out, and what is missing if not.
     *
     * @param sellable all three conditions met
     */
    public record Readiness(
            UUID batchId, boolean priced, boolean hasMrp, boolean labelled) {

        public boolean sellable() {
            return priced && hasMrp && labelled;
        }

        /** In the words someone on the floor would use, not the schema's. */
        public List<String> missing() {
            List<String> missing = new ArrayList<>();
            if (!priced) {
                missing.add("no price set");
            }
            if (!hasMrp) {
                missing.add("no MRP read off the goods");
            }
            if (!labelled) {
                missing.add("not labelled");
            }
            return missing;
        }

        /** What stands between these goods and a label, which is a shorter list. */
        public List<String> missingBeforeLabelling() {
            List<String> missing = new ArrayList<>();
            if (!priced) {
                missing.add("no price set");
            }
            if (!hasMrp) {
                missing.add("no MRP read off the goods");
            }
            return missing;
        }
    }

    /**
     * What goes on the tag: the printed MRP, our price, and the saving in both rupees and
     * percent — the shop's whole proposition, checkable by the customer against the pack.
     */
    public record LabelContent(
            UUID batchId,
            String productName,
            Money mrp,
            Money sellingPrice,
            Money saving,
            long savingPercent,
            boolean mrpIsEstimate) {}
}
