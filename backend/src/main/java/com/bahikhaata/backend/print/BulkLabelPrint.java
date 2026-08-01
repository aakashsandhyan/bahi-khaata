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
package com.bahikhaata.backend.print;

import com.bahikhaata.backend.catalog.Barcode;
import com.bahikhaata.backend.catalog.BarcodeResolver;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.contracts.AwaitingLabelProduct;
import com.bahikhaata.contracts.BulkPrintResult;
import com.bahikhaata.contracts.Origin;
import com.bahikhaata.contracts.PrintLabelRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bulk label screen and its run: list priced shelf products still awaiting a label, and print a
 * chosen set of them paired onto the two-up rows.
 *
 * <p>Within one run the products are paired consecutively into {@link LabelTemplateService#renderRow}
 * documents, so N products print ceil(N/2) rows; a lone leftover prints as a duplicate pair rather
 * than a blank sticker. A row that prints successfully marks its product(s) label-printed; a row
 * that fails leaves them for a later run. This is a synchronous operator action, not the async
 * single-job queue — the operator sees the outcome directly.
 */
@Service
public class BulkLabelPrint {

    private static final Logger log = LoggerFactory.getLogger(BulkLabelPrint.class);

    private final ProductRepository products;
    private final BarcodeRepository barcodes;
    private final BarcodeResolver barcodeResolver;
    private final BatchRepository batches;
    private final LabelTemplateService labelService;
    private final PrinterDriver printerDriver;

    public BulkLabelPrint(
            ProductRepository products,
            BarcodeRepository barcodes,
            BarcodeResolver barcodeResolver,
            BatchRepository batches,
            LabelTemplateService labelService,
            PrinterDriver printerDriver) {
        this.products = products;
        this.barcodes = barcodes;
        this.barcodeResolver = barcodeResolver;
        this.batches = batches;
        this.labelService = labelService;
        this.printerDriver = printerDriver;
    }

    /**
     * Resolves any barcode (BBZ shelf code, or the original LSN/ASIN) to its priced product, for the
     * reprint screen — the one place a barcode can be looked up. Refuses an unknown code and an
     * as-yet-unpriced product with a clear message; the label always carries the product's BBZ.
     */
    @Transactional(readOnly = true)
    public AwaitingLabelProduct labelByBarcode(String code) {
        Product product = barcodeResolver.resolve(code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No product found for barcode \"" + code + "\"."));
        if (!product.isPriced()) {
            throw new IllegalStateException(
                    "\"" + product.getName() + "\" is not priced yet — price it before printing a label.");
        }
        return toAwaiting(product);
    }

    /** Priced shelf products whose label has not printed, newest-name first, for the bulk screen. */
    @Transactional(readOnly = true)
    public List<AwaitingLabelProduct> awaitingLabel() {
        return products.findBySellingPriceIsNotNullAndLabelPrintedAtIsNullOrderByName().stream()
                .map(this::toAwaiting)
                .toList();
    }

    /**
     * Prints labels for the given products, paired onto rows. Returns how many printed and how many
     * failed. Products that are missing or unpriced are skipped as failures.
     */
    @Transactional
    public BulkPrintResult printBulk(List<UUID> productIds) {
        List<Product> toPrint = new ArrayList<>();
        int skipped = 0;
        for (UUID id : productIds) {
            Product p = products.findById(id).filter(Product::isPriced).orElse(null);
            if (p == null) {
                skipped++;
            } else {
                toPrint.add(p);
            }
        }

        int printed = 0;
        int failed = skipped;
        for (int i = 0; i < toPrint.size(); i += LabelTemplateService.LABELS_PER_ROW) {
            Product left = toPrint.get(i);
            Product right = i + 1 < toPrint.size() ? toPrint.get(i + 1) : left; // odd → duplicate pair
            int inRow = right == left ? 1 : 2;
            try {
                printerDriver.sendLabel(labelService.renderRow(labelFor(left), labelFor(right)), 1);
                markPrinted(left);
                if (right != left) {
                    markPrinted(right);
                }
                printed += inRow;
            } catch (PrinterDriver.PrinterException e) {
                failed += inRow;
                log.warn("Bulk print row failed: {}", e.getMessage());
            }
        }
        return new BulkPrintResult(printed, failed);
    }

    private void markPrinted(Product product) {
        product.markLabelPrinted(Instant.now());
        products.save(product);
    }

    private AwaitingLabelProduct toAwaiting(Product product) {
        return new AwaitingLabelProduct(
                product.getId(),
                bbzFor(product),
                product.getName(),
                product.getSellingPrice().paise(),
                confirmedMrpPaise(product));
    }

    private PrintLabelRequest labelFor(Product product) {
        return new PrintLabelRequest(
                bbzFor(product),
                product.getName(),
                confirmedMrpPaise(product),
                product.getSellingPrice().paise());
    }

    /** The product's BBZ code — the shelf-scannable identity assigned at pricing. */
    private String bbzFor(Product product) {
        return barcodes.findByProductId(product.getId()).stream()
                .filter(b -> b.getOrigin() == Origin.INTERNAL)
                .findFirst()
                .map(Barcode::getCode)
                .orElse("");
    }

    /** The newest confirmed (non-estimate) MRP for the product, or null — only that may be struck. */
    private Long confirmedMrpPaise(Product product) {
        return batches.findByProductIdNewestFirst(product.getId()).stream()
                .filter(b -> b.getMrp() != null && !b.isMrpEstimate())
                .findFirst()
                .map(Batch::getMrp)
                .map(m -> m.paise())
                .orElse(null);
    }
}
