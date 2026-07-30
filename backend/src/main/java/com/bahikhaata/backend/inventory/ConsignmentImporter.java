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

import com.bahikhaata.backend.catalog.Barcode;
import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.ImportResult;
import com.bahikhaata.contracts.Marketplace;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.Origin;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records what a supplier says is coming.
 *
 * <p>An import writes an expectation and nothing more: the lot, the cartons it should arrive
 * in, the lines each carton should contain, and catalogue entries for the products named. It
 * writes <em>no stock</em>. Stock appears when someone opens a box and counts what is actually
 * inside, which is a different act on a different day.
 *
 * <p>That separation is the point of this class. The first real consignment put 3,583 units on
 * hand on a supplier's word with no box opened, in a trade whose defining trait is that
 * manifests and deliveries disagree. A manifest is a claim; recording it as fact states
 * quantities nobody has counted.
 *
 * <p>Cost is not apportioned here either. Every line's share depends on every other line, so
 * no share can be settled while a carton is still unopened. That happens when the lot closes,
 * over what actually arrived.
 *
 * <p>Everything in one transaction. A consignment half-recorded is worse than one not recorded
 * at all: cartons that exist in the system but not on the floor, or expectations that no
 * longer match the paperwork, are both harder to find and undo than simply importing again.
 *
 * <p>Not carried over from the one-act importer: a per-line pinned cost. No manifest seen so
 * far states one, `expected_line` has no column for it, and the weight it would have supplied
 * is now `stated_value_paise`. If a supplier ever itemises costs that must survive
 * apportionment, that is a column and a decision, not an oversight to be patched quietly.
 */
@Service
public class ConsignmentImporter {

    private final ProductRepository products;
    private final BarcodeRepository barcodes;
    private final LotRepository lots;
    private final BoxRepository boxes;
    private final BoxReceiptRepository boxReceipts;
    private final ExpectedLineRepository expectedLines;
    private final SupplierService suppliers;
    private final JdbcTemplate jdbc;

    ConsignmentImporter(
            ProductRepository products,
            BarcodeRepository barcodes,
            LotRepository lots,
            BoxRepository boxes,
            BoxReceiptRepository boxReceipts,
            ExpectedLineRepository expectedLines,
            SupplierService suppliers,
            JdbcTemplate jdbc) {
        this.products = products;
        this.barcodes = barcodes;
        this.lots = lots;
        this.boxes = boxes;
        this.boxReceipts = boxReceipts;
        this.expectedLines = expectedLines;
        this.suppliers = suppliers;
        this.jdbc = jdbc;
    }

    @Transactional
    public ImportResult importConsignment(ImportConsignmentRequest request) {
        LocalDate receivedOn = LocalDate.parse(request.receivedOn());
        // Resolved once for the whole consignment: every lot it creates links this supplier.
        Supplier supplier = suppliers.resolveActiveSupplier(request.supplierId());
        List<String> warnings = new ArrayList<>();

        int lotsCreated = 0;
        int boxesCreated = 0;
        int linesCreated = 0;
        int productsCreated = 0;
        int productsMatched = 0;
        long unitsExpected = 0;

        for (ImportLot importLot : request.lots()) {
            if (importLot.lines().isEmpty()) {
                warnings.add(importLot.categoryCode() + ": no lines, skipped");
                continue;
            }
            requireCategory(importLot.categoryCode());

            Lot lot =
                    lots.save(
                            new Lot(
                                    supplier,
                                    receivedOn,
                                    Money.ofPaise(importLot.amountPaidPaise()),
                                    Money.ZERO,
                                    importLot.allocationMethod()));
            lotsCreated++;

            Map<String, Box> boxesByTracking = new LinkedHashMap<>();

            // Combined per carton, not per product: a manifest lists rows, and the same
            // product appears repeatedly within one box. Two lines for one product in one box
            // would breach the box/product uniqueness and split what is really one thing to
            // find. Across boxes they stay separate, because each carton is opened on its own.
            for (Map.Entry<BoxLine, CombinedLine> entry : combine(importLot.lines(), warnings)) {
                BoxLine key = entry.getKey();
                CombinedLine line = entry.getValue();

                Box box =
                        boxesByTracking.computeIfAbsent(
                                key.trackingNumber, tracking -> {
                                    Box b = boxes.save(new Box(lot, tracking));
                                    boxReceipts.save(new BoxReceipt(lot.getId(), tracking));
                                    return b;
                                });

                Product product = existingProduct(line.code);
                if (product == null) {
                    product =
                            products.save(
                                    new Product(
                                            line.name,
                                            Category.of(importLot.categoryCode()),
                                            Map.of("importedFrom", supplier.getName())));
                    // The supplier's code becomes the product's barcode. It is already on the
                    // goods and already unique, so minting an internal one would mean two
                    // codes for the same thing.
                    barcodes.save(new Barcode(product, line.code, Origin.MARKETPLACE));
                    productsCreated++;
                } else {
                    productsMatched++;
                }

                Long onlinePrice = line.averageOnlinePricePaise();
                if (onlinePrice != null) {
                    product.observeOnlinePrice(
                            Money.ofPaise(onlinePrice), line.onlinePriceSource, receivedOn);
                }

                expectedLines.save(
                        new ExpectedLine(
                                lot,
                                box,
                                product,
                                line.code,
                                line.quantity,
                                line.getStatedValuePaise() == null
                                        ? null
                                        : Money.ofPaise(line.getStatedValuePaise())));

                linesCreated++;
                unitsExpected += line.quantity;
            }

            boxesCreated += boxesByTracking.size();
        }

        warnings.add(
                unitsExpected
                        + " units are expected but none are on hand. Stock arrives when the"
                        + " cartons are opened and counted.");

        return new ImportResult(
                lotsCreated, boxesCreated, linesCreated, productsCreated, productsMatched,
                unitsExpected, warnings);
    }

    /** Fails early on an unknown category, rather than on a foreign key deep in the flush. */
    private void requireCategory(String code) {
        Integer found =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM category WHERE code = ?", Integer.class, code);
        if (found == null || found == 0) {
            throw new IllegalArgumentException(
                    "no such category: " + code + ". Add it to the category table first.");
        }
    }

    private Product existingProduct(String code) {
        return barcodes.findByCode(code).map(Barcode::getProduct).orElse(null);
    }

    private List<Map.Entry<BoxLine, CombinedLine>> combine(
            List<ImportLine> lines, List<String> warnings) {
        Map<BoxLine, CombinedLine> combined = new LinkedHashMap<>();
        for (ImportLine line : lines) {
            combined
                    .computeIfAbsent(
                            new BoxLine(line.trackingNumber(), line.code()),
                            key -> new CombinedLine(line.code(), line.name()))
                    .add(line);
        }
        long collapsed = lines.size() - combined.size();
        if (collapsed > 0) {
            warnings.add(collapsed + " rows combined into an existing line for the same carton");
        }
        return List.copyOf(combined.entrySet());
    }

    /** One product within one carton — what someone will look for when that box is opened. */
    private record BoxLine(String trackingNumber, String code) {}

    /**
     * A product's rows within one carton, added together.
     *
     * <p>The stated value handed on is <em>per unit</em>, not the line's total. Three units at
     * ₹100 do attract three times what one does, but that multiplication belongs to the
     * allocation, which already applies quantity. Passing a total applies it twice: the weight
     * becomes value × quantity², multi-unit lines take an inflated share, and single-unit lines
     * are squeezed to make room.
     *
     * <p>Nothing about the lot's total gives this away — the shares still sum to what was paid,
     * because they are shares. Only the split is wrong.
     */
    private static final class CombinedLine {
        private final String code;
        private final String name;
        private long quantity;
        private long statedValueTotalPaise;
        private boolean anyStatedValue;
        private long onlinePricePaise;
        private long onlinePricedQuantity;
        private Marketplace onlinePriceSource;

        CombinedLine(String code, String name) {
            this.code = code;
            this.name = name;
        }

        void add(ImportLine line) {
            quantity += line.quantity();
            if (line.weighingValuePaise() > 0) {
                statedValueTotalPaise += line.weighingValuePaise() * line.quantity();
                anyStatedValue = true;
            }
            // Averaged over the units that carried a price, not taken from whichever row came
            // last. These are returns, so the same product genuinely sold at different prices
            // on different days, and one arbitrary row is not a fair account of any of them.
            if (line.onlinePricePaise() != null) {
                if (onlinePriceSource != null && onlinePriceSource != line.onlinePriceSource()) {
                    throw new IllegalArgumentException(
                            code
                                    + ": rows quote prices from two marketplaces ("
                                    + onlinePriceSource
                                    + " and "
                                    + line.onlinePriceSource()
                                    + "). Averaging across markets would produce a figure that"
                                    + " holds on neither, so the manifest needs splitting first.");
                }
                onlinePricePaise += line.onlinePricePaise() * line.quantity();
                onlinePricedQuantity += line.quantity();
                onlinePriceSource = line.onlinePriceSource();
            }
        }

        /** The per-unit weight for apportioning, or null if the manifest stated none. */
        Long getStatedValuePaise() {
            return anyStatedValue ? statedValueTotalPaise / quantity : null;
        }

        /** The average price its units sold at online, or null if no row stated one. */
        Long averageOnlinePricePaise() {
            return onlinePricedQuantity == 0 ? null : onlinePricePaise / onlinePricedQuantity;
        }
    }
}
