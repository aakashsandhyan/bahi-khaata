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
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.Origin;
import com.bahikhaata.contracts.SuggestedMrp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filling in printed prices nobody has read yet, in the background.
 *
 * <p>Goods counted out of a carton cannot be sold until an MRP is recorded, and reading 3,583
 * packs by hand is the slowest part of getting a delivery onto the shelf. Where a marketplace
 * still lists the goods, the printed price is usually there to be found.
 *
 * <p>What it writes is always an <strong>estimate</strong>. MRP is a legal figure printed on a
 * pack; a number fetched from a website is evidence about that figure, not the figure. It never
 * overwrites one somebody read off the goods, and anyone holding the pack overrides it.
 *
 * <p>It never blocks anything. Called deliberately rather than on a timer, so it cannot start
 * competing with someone unpacking, and a failure is logged rather than raised — the shop
 * carries on exactly as it would with no internet at all, which is a state it must survive.
 */
@Service
public class MrpBackfill {

    private static final Logger log = LoggerFactory.getLogger(MrpBackfill.class);

    private final MrpLookup lookup;
    private final BatchRepository batches;
    private final BarcodeRepository barcodes;

    MrpBackfill(MrpLookup lookup, BatchRepository batches, BarcodeRepository barcodes) {
        this.lookup = lookup;
        this.batches = batches;
        this.barcodes = barcodes;
    }

    /**
     * Looks up every counted item that has no MRP, and records what is found as an estimate.
     *
     * @param limit how many to attempt, so a first run can be tried small before it is trusted
     */
    @Transactional
    public Outcome run(int limit) {
        if (!lookup.isAvailable()) {
            return new Outcome(0, 0, 0, lookup.unavailableReason());
        }

        // Only stock actually held. Looking up goods that never arrived spends money on
        // questions nobody asked.
        List<Batch> waiting =
                batches.findAll().stream()
                        .filter(batch -> batch.getMrp() == null)
                        .limit(limit)
                        .toList();
        if (waiting.isEmpty()) {
            return new Outcome(0, 0, 0, "Nothing is waiting on a price.");
        }

        Map<String, List<Batch>> byAsin = new LinkedHashMap<>();
        for (Batch batch : waiting) {
            marketplaceCodeOf(batch.getProduct().getId())
                    .ifPresent(asin -> byAsin.computeIfAbsent(asin, key -> new ArrayList<>())
                            .add(batch));
        }
        if (byAsin.isEmpty()) {
            return new Outcome(waiting.size(), 0, 0,
                    "None of these carry a marketplace reference to look up.");
        }

        Map<String, Money> found = lookup.lookup(List.copyOf(byAsin.keySet()));

        int recorded = 0;
        int refused = 0;
        for (Map.Entry<String, List<Batch>> entry : byAsin.entrySet()) {
            Money price = found.get(entry.getKey());
            if (price == null) {
                continue;
            }
            for (Batch batch : entry.getValue()) {
                try {
                    // Marked an estimate, and subject to the same plausibility checks as a
                    // figure typed by hand — a lookup is no more trustworthy than a person.
                    batch.recordMrp(price, true);
                    recorded++;
                } catch (RuntimeException e) {
                    refused++;
                    log.info("Refused a looked-up price for {}: {}", entry.getKey(),
                            e.getMessage());
                }
            }
        }

        return new Outcome(
                byAsin.size(),
                recorded,
                refused,
                recorded + " price(s) found and recorded as estimates. Anyone holding the goods"
                        + " should still read the pack — an estimate is evidence, not the"
                        + " printed figure.");
    }

    /**
     * Looks up one line's printed price, for someone to accept or ignore.
     *
     * <p>Called while the goods are in hand, so it is deliberately not applied: the pack itself
     * is better evidence than any website, and the person holding it can simply read it. This
     * exists for the packs where the figure has rubbed off, or was never printed.
     */
    @Transactional(readOnly = true)
    public SuggestedMrp suggestFor(UUID productId) {
        if (!lookup.isAvailable()) {
            return SuggestedMrp.none(lookup.unavailableReason());
        }
        return marketplaceCodeOf(productId)
                .map(
                        asin -> {
                            Money price = lookup.lookup(List.of(asin)).get(asin);
                            return price == null
                                    ? SuggestedMrp.none("No printed price is listed for these"
                                            + " goods. Read it off the pack.")
                                    : new SuggestedMrp(price.paise(), "Amazon listing", null);
                        })
                .orElseGet(() -> SuggestedMrp.none(
                        "Nothing to look these up by. Read the price off the pack."));
    }

    /** The supplier's marketplace reference for a product, which is what a lookup takes. */
    private java.util.Optional<String> marketplaceCodeOf(UUID productId) {
        return barcodes.findByProductId(productId).stream()
                .filter(barcode -> barcode.getOrigin() == Origin.MARKETPLACE)
                .map(Barcode::getCode)
                .findFirst();
    }

    /**
     * What a run did.
     *
     * @param refused prices that came back and were rejected as implausible — worth knowing,
     *     since a source returning nonsense should be noticed rather than absorbed
     */
    public record Outcome(int attempted, int recorded, int refused, String message) {}
}
