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
package com.bahikhaata.backend.lookup;

import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.SuggestedMrp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

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
 * <p>It never blocks anything. This class does the <em>network</em> half and holds no transaction
 * while it does — the look-up takes seconds a page and SQLite runs on a single connection, so a
 * scrape inside a transaction would freeze every scan and sale until it finished. The database half
 * is {@link MrpApplier}, reached across a proxy so each write is its own short, committed unit. A
 * failure is logged rather than raised — the shop carries on exactly as it would with no internet
 * at all, which is a state it must survive.
 */
@Service
public class MrpBackfill {

    private final MrpLookup lookup;
    private final MrpApplier applier;

    MrpBackfill(MrpLookup lookup, MrpApplier applier) {
        this.lookup = lookup;
        this.applier = applier;
    }

    /**
     * Looks up a bounded batch of unpriced items and records what is found — a small first run to be
     * tried before the source is trusted. The look-up runs outside any transaction.
     *
     * @param limit how many distinct references to attempt
     */
    public Outcome run(int limit) {
        if (!lookup.isAvailable()) {
            return new Outcome(0, 0, 0, lookup.unavailableReason());
        }
        List<String> asins = applier.waiting(limit);
        if (asins.isEmpty()) {
            return new Outcome(0, 0, 0,
                    "Nothing is waiting on a price that has not already been tried.");
        }
        Map<String, Money> found = lookup.lookup(asins); // slow network, no transaction held
        MrpApplier.Applied applied = applier.apply(asins, found);
        return new Outcome(
                asins.size(),
                applied.recorded(),
                applied.refused(),
                applied.recorded() + " price(s) found and recorded as estimates. Anyone holding the"
                        + " goods should still read the pack — an estimate is evidence, not the"
                        + " printed figure.");
    }

    /**
     * Every distinct marketplace reference held with no price yet and not already tried — the work
     * list for a background fill, snapshotted once so the fill walks each item a single time.
     */
    public List<String> waitingAsins() {
        return applier.waitingUntried();
    }

    /**
     * Un-marks a set of references, for a run that stopped because the source was blocking. What a
     * blocked source appeared to try was never a fair test, so it is freed to try again later.
     */
    public void forget(List<String> asins) {
        applier.forget(asins);
    }

    /**
     * Looks up one chunk and records what is found. The scrape runs here with no transaction open,
     * so the single database connection stays free for scans and sales while it waits on the
     * network; only the short write that follows touches the database. Every product in the chunk is
     * marked as tried, found or not, so no later run scrapes it again.
     */
    public int fillChunk(List<String> asins) {
        if (!lookup.isAvailable() || asins.isEmpty()) {
            return 0;
        }
        Map<String, Money> found = lookup.lookup(asins); // slow network, no transaction held
        return applier.apply(asins, found).recorded();
    }

    /**
     * Looks up one line's printed price, for someone to accept or ignore.
     *
     * <p>Called while the goods are in hand, so it is deliberately not applied: the pack itself is
     * better evidence than any website, and the person holding it can simply read it. This exists
     * for the packs where the figure has rubbed off, or was never printed. The scrape runs outside
     * any transaction, so a suggestion never freezes a scan happening at the same time.
     */
    public SuggestedMrp suggestFor(UUID productId) {
        if (!lookup.isAvailable()) {
            return SuggestedMrp.none(lookup.unavailableReason());
        }
        return applier
                .marketplaceCode(productId)
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

    /**
     * What a run did.
     *
     * @param refused prices that came back and were rejected as implausible — worth knowing, since a
     *     source returning nonsense should be noticed rather than absorbed
     */
    public record Outcome(int attempted, int recorded, int refused, String message) {}
}
