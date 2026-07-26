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

import com.bahikhaata.contracts.BoxCountEntry;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.ProductBoxLine;
import com.bahikhaata.contracts.ProductCountRequest;
import com.bahikhaata.contracts.ProductCountResult;
import com.bahikhaata.contracts.ProductLotLines;
import com.bahikhaata.contracts.RejectedEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counting a product across the boxes of one delivery in a single act — the lane parallel to
 * box-by-box counting, for goods that can be identified.
 *
 * <p>The same product sits on several boxes' sheets, so counting it box by box means finding the
 * same stack ten times. Here the operator (having chosen the lot and the product in the catalogue)
 * enters what was found per box and submits once. Each box's line is drawn down through the very same
 * {@link GoodsInCounting#countExpected} the box flow uses, so batch, ledger, and MRP inheritance are
 * written identically — this adds a way to reach those lines, not a second way to write them.
 *
 * <p>Two guards make the shortcut safe. A quantity is capped at its line's current outstanding, so a
 * bulk count can never push a line above the manifest. And each entry carries the outstanding the
 * operator saw when the grid loaded; if another station has counted into that box since, the entry is
 * refused with the new figure rather than counted on top — optimistic concurrency, the answer to the
 * double-count two laptops could otherwise cause.
 *
 * <p>It touches nothing of the receiving lifecycle: a box's received state is left exactly as
 * box-by-box counting leaves it. Tagless goods, having no identifier to resolve, stay box-centric.
 */
@Service
public class ProductCounting {

    private final ExpectedLineRepository expectedLines;
    private final LotRepository lots;
    private final GoodsInCounting counting;

    ProductCounting(
            ExpectedLineRepository expectedLines, LotRepository lots, GoodsInCounting counting) {
        this.expectedLines = expectedLines;
        this.lots = lots;
        this.counting = counting;
    }

    /**
     * A product's outstanding box-lines within one open lot — the grid to count against. Lines
     * already fully counted drop out; a closed lot offers nothing, since it is not being counted.
     */
    @Transactional(readOnly = true)
    public ProductLotLines linesFor(UUID lotId, UUID productId) {
        Lot lot =
                lots.findById(lotId)
                        .orElseThrow(() -> new IllegalArgumentException("no such lot: " + lotId));
        List<ProductBoxLine> lines = new ArrayList<>();
        String productName = null;
        if (lot.isOpen()) {
            for (ExpectedLine line :
                    expectedLines.findByLotIdAndProductIdOrderByCode(lotId, productId)) {
                productName = line.getProduct().getName();
                long outstanding = line.getQuantityExpected() - line.getQuantityCounted();
                if (outstanding > 0) {
                    lines.add(
                            new ProductBoxLine(
                                    line.getId(), line.getBox().getTrackingNumber(), outstanding));
                }
            }
        }
        return new ProductLotLines(productId, productName, lotId, lines);
    }

    /**
     * Records a whole product-centric count in one transaction. Each entry is checked against its
     * line's current outstanding: a line that moved since the grid loaded is refused (returned for
     * re-entry), an untouched line is counted, capped at its outstanding, through the shared count
     * path. Accepted entries commit together; refused ones change nothing.
     */
    @Transactional
    public ProductCountResult count(ProductCountRequest request) {
        Lot lot =
                lots.findById(request.lotId())
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "no such lot: " + request.lotId()));
        if (!lot.isOpen()) {
            throw new IllegalArgumentException("that delivery is closed and cannot be counted.");
        }
        Money mrp = request.mrpPaise() == null ? null : Money.ofPaise(request.mrpPaise());
        Instant at = Instant.now();

        List<RejectedEntry> rejected = new ArrayList<>();
        int linesCounted = 0;
        long unitsCounted = 0;

        for (BoxCountEntry entry : request.entries()) {
            ExpectedLine line =
                    expectedLines
                            .findById(entry.lineId())
                            .orElseThrow(
                                    () -> new IllegalArgumentException(
                                            "no such line: " + entry.lineId()));
            // Guard: the line must belong to the product and lot the request names, so the endpoint
            // cannot be driven to count a line that is not part of this grid.
            if (!line.getProduct().getId().equals(request.productId())
                    || !line.getLot().getId().equals(request.lotId())) {
                throw new IllegalArgumentException(
                        "line " + entry.lineId() + " is not part of this product and delivery.");
            }

            long outstanding = line.getQuantityExpected() - line.getQuantityCounted();
            // Optimistic concurrency: the grid's figure must still hold, or another station moved it.
            if (outstanding != entry.outstandingSeen()) {
                rejected.add(
                        new RejectedEntry(
                                line.getId(), line.getBox().getTrackingNumber(), outstanding));
                continue;
            }
            long quantity = Math.min(Math.max(0, entry.quantity()), outstanding);
            if (quantity <= 0) {
                continue;
            }
            counting.countExpected(
                    line.getId(), request.condition(), quantity, mrp, request.mrpIsEstimate(),
                    null, null, at);
            linesCounted++;
            unitsCounted += quantity;
        }
        return new ProductCountResult(linesCounted, unitsCounted, rejected);
    }
}
