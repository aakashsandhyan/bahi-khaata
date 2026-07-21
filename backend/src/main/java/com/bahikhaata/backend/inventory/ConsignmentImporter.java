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
import com.bahikhaata.backend.inventory.allocation.AllocatedLine;
import com.bahikhaata.backend.inventory.allocation.Allocation;
import com.bahikhaata.backend.inventory.allocation.AllocationLine;
import com.bahikhaata.backend.inventory.allocation.CostAllocator;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.ImportResult;
import com.bahikhaata.contracts.Marketplace;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.Origin;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a supplier's consignment: its lots, the products in them, their costs, and the stock
 * they bring on hand.
 *
 * <p>Everything in one transaction. A consignment half-recorded is worse than one not recorded
 * at all: stock that exists in the system but not on the shelf, or costs that no longer sum to
 * what was paid, are both harder to find and undo than simply doing it again.
 *
 * <p>What this does <em>not</em> do is claim the goods have been checked. It records what the
 * supplier says arrived, so there is something to unpack against. MRP is deliberately left
 * unset — a manifest never carries the printed maximum retail price — so every product lands
 * unsellable until someone reads one off the goods.
 */
@Service
public class ConsignmentImporter {

    private final ProductRepository products;
    private final BarcodeRepository barcodes;
    private final LotRepository lots;
    private final BatchRepository batches;
    private final StockLedgerRepository ledger;
    private final CostAllocator allocator;
    private final JdbcTemplate jdbc;

    ConsignmentImporter(
            ProductRepository products,
            BarcodeRepository barcodes,
            LotRepository lots,
            BatchRepository batches,
            StockLedgerRepository ledger,
            CostAllocator allocator,
            JdbcTemplate jdbc) {
        this.products = products;
        this.barcodes = barcodes;
        this.lots = lots;
        this.batches = batches;
        this.ledger = ledger;
        this.allocator = allocator;
        this.jdbc = jdbc;
    }

    @Transactional
    public ImportResult importConsignment(ImportConsignmentRequest request) {
        LocalDate receivedOn = LocalDate.parse(request.receivedOn());
        List<String> warnings = new ArrayList<>();

        int created = 0;
        int matched = 0;
        long units = 0;
        long allocatedPaise = 0;
        int awaitingMrp = 0;

        for (ImportLot importLot : request.lots()) {
            if (importLot.lines().isEmpty()) {
                warnings.add(importLot.categoryCode() + ": no lines, skipped");
                continue;
            }
            requireCategory(importLot.categoryCode());

            // Combined first: a manifest lists one row per physical unit, so the same product
            // appears many times in a category. Two lines for one product would halve its
            // allocation and breach the one-line-per-product-per-lot rule.
            Map<String, CombinedLine> combined = combine(importLot.lines(), warnings);

            Allocation allocation =
                    allocator.allocate(
                            Money.ofPaise(importLot.amountPaidPaise()),
                            Money.ZERO,
                            combined.values().stream().map(CombinedLine::toAllocationLine).toList());

            Lot lot =
                    lots.save(
                            new Lot(
                                    request.supplier(),
                                    receivedOn,
                                    Money.ofPaise(importLot.amountPaidPaise()),
                                    Money.ZERO,
                                    importLot.allocationMethod()));

            int index = 0;
            for (CombinedLine line : combined.values()) {
                AllocatedLine allocated = allocation.lines().get(index++);

                Product product = existingProduct(line.code);
                if (product == null) {
                    product =
                            products.save(
                                    new Product(
                                            line.name,
                                            Category.of(importLot.categoryCode()),
                                            Map.of("importedFrom", request.supplier())));
                    // The supplier's code becomes the product's barcode. It is already on the
                    // goods and already unique, so inventing an internal one would mean two
                    // codes for the same thing.
                    barcodes.save(new Barcode(product, line.code, Origin.MANUFACTURER));
                    created++;
                } else {
                    matched++;
                }

                // Only where the manifest actually states a market price. A cost-plus sheet
                // has none, and the field stays null rather than being filled with a cost.
                Long onlinePrice = line.averageOnlinePricePaise();
                if (onlinePrice != null) {
                    product.observeOnlinePrice(
                            Money.ofPaise(onlinePrice), line.onlinePriceSource, receivedOn);
                }

                Batch batch =
                        batches.save(
                                new Batch(
                                        product,
                                        lot,
                                        allocated.allocatedTotal(),
                                        allocated.allocatedUnitCost(),
                                        allocated.basis(),
                                        line.quantity,
                                        0,
                                        // No MRP: a manifest does not carry the printed price.
                                        null,
                                        false));

                ledger.save(
                        StockLedgerEntry.receiptOf(
                                batch, receivedOn.atStartOfDay(ZoneOffset.UTC).toInstant()));

                units += line.quantity;
                allocatedPaise += allocated.allocatedTotal().paise();
                awaitingMrp++;
            }
        }

        warnings.add(
                awaitingMrp
                        + " products are on hand but cannot be sold until an MRP is read off"
                        + " them and a price is set.");

        return new ImportResult(
                request.lots().size(), created, matched, units, allocatedPaise, awaitingMrp,
                warnings);
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

    private Map<String, CombinedLine> combine(List<ImportLine> lines, List<String> warnings) {
        Map<String, CombinedLine> combined = new java.util.LinkedHashMap<>();
        for (ImportLine line : lines) {
            combined.computeIfAbsent(line.code(), code -> new CombinedLine(code, line.name()))
                    .add(line);
        }
        long collapsed = lines.size() - combined.size();
        if (collapsed > 0) {
            warnings.add(collapsed + " rows combined into existing products of the same code");
        }
        return combined;
    }

    /**
     * A product's rows within one lot, added together.
     *
     * <p>The weighing value handed on is <em>per unit</em>, not the line's total. Three units
     * at ₹100 do attract three times what one does, but that multiplication belongs to the
     * weighting strategy, which already applies {@code quantityReceived}. Passing a total here
     * applies it twice: the weight becomes value × quantity², multi-unit lines take an
     * inflated share, and single-unit lines are squeezed to make room.
     *
     * <p>Nothing about the lot's total gives this away — the shares still sum to what was
     * paid, because they are shares. Only the distribution is wrong, which is why
     * {@code QuantityIsNotCountedTwiceTest} compares lines against each other rather than
     * against the total.
     */
    private static final class CombinedLine {
        private final String code;
        private final String name;
        private long quantity;
        private long weighingPaise;
        private Long pinnedUnitCostPaise;
        private long onlinePricePaise;
        private long onlinePricedQuantity;
        private Marketplace onlinePriceSource;

        CombinedLine(String code, String name) {
            this.code = code;
            this.name = name;
        }

        void add(ImportLine line) {
            quantity += line.quantity();
            // Accumulated as a total so rows of differing value average correctly, then
            // handed back per unit below.
            weighingPaise += line.weighingValuePaise() * line.quantity();
            if (line.pinnedUnitCostPaise() != null) {
                pinnedUnitCostPaise = line.pinnedUnitCostPaise();
            }
            // Averaged over the units that carried a price, not taken from whichever row came
            // last. These are returns, so the same product genuinely sold at different prices
            // on different days, and one arbitrary row is not a fair account of any of them.
            //
            // Weighted by quantity, matching how the cost weighing above is combined: a price
            // seven units sold at should count for more than one a single unit did.
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

        /** The average price its units sold at online, or null if no row stated one. */
        Long averageOnlinePricePaise() {
            return onlinePricedQuantity == 0 ? null : onlinePricePaise / onlinePricedQuantity;
        }

        AllocationLine toAllocationLine() {
            return new AllocationLine(
                    code,
                    quantity,
                    0,
                    Money.ofPaise(weighingPaise / quantity),
                    pinnedUnitCostPaise == null ? null : Money.ofPaise(pinnedUnitCostPaise));
        }
    }
}
