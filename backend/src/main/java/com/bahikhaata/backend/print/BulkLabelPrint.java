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
package com.bahikhaata.backend.print;

import com.bahikhaata.backend.catalog.Barcode;
import com.bahikhaata.backend.catalog.BarcodeResolver;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.backend.inventory.StockLevels;
import com.bahikhaata.backend.pricing.ShelfPricing;
import com.bahikhaata.contracts.AwaitingLabelProduct;
import com.bahikhaata.contracts.BulkPrintResult;
import com.bahikhaata.contracts.LabelReviewEditRequest;
import com.bahikhaata.contracts.LabelReviewEntry;
import com.bahikhaata.contracts.Origin;
import com.bahikhaata.contracts.PriceExistingRequest;
import com.bahikhaata.contracts.PrintLabelRequest;
import com.bahikhaata.contracts.QueueAwaitingResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final StockLevels stock;
    private final PrintJobRepository printJobs;
    private final LabelTemplateService labelService;
    private final PrinterDriver printerDriver;
    private final ShelfPricing shelfPricing;

    private static final String REVIEW = "review";

    public BulkLabelPrint(
            ProductRepository products,
            BarcodeRepository barcodes,
            BarcodeResolver barcodeResolver,
            BatchRepository batches,
            StockLevels stock,
            PrintJobRepository printJobs,
            LabelTemplateService labelService,
            PrinterDriver printerDriver,
            ShelfPricing shelfPricing) {
        this.products = products;
        this.barcodes = barcodes;
        this.barcodeResolver = barcodeResolver;
        this.batches = batches;
        this.stock = stock;
        this.printJobs = printJobs;
        this.labelService = labelService;
        this.printerDriver = printerDriver;
        this.shelfPricing = shelfPricing;
    }

    /**
     * Puts a product's labels on the review queue as a single entry — one row per product, not one
     * per sticker — so a reviewer sees the whole pricing command in one go. Called after each price
     * or re-price with {@code enteredQty}, the quantity the operator entered on the pricing screen:
     * the labels are for exactly what they said they put out, not for pre-existing stock.
     *
     * <p>The entry is upserted in place, so its id is stable and a reviewer mid-edit is not thrown
     * off. A new entry carries {@code enteredQty}; on a later pricing the reviewer's chosen count is
     * kept and this command's entered quantity is added on top. Everything is capped at what is on
     * hand (never label more than exists). Nothing entered, and no entry yet, makes no entry — a
     * corrected label with no new stock is reprinted from the Reprint screen.
     */
    @Transactional
    public void enqueueForReview(UUID productId, long enteredQty, String operatorName) {
        Product product = products.findById(productId).filter(Product::isPriced).orElse(null);
        PrintJob existing = printJobs.findFirstByProductIdAndStatus(productId, REVIEW).orElse(null);
        long onHand = product == null ? 0 : stock.onHand(productId);
        String bbz = product == null ? "" : bbzFor(product);
        if (product == null || onHand <= 0 || bbz.isEmpty()) {
            if (existing != null) {
                printJobs.delete(existing);
            }
            return;
        }
        long added = Math.max(0, enteredQty);
        if (existing == null) {
            // Label exactly what this pricing command put out, capped at what is on hand.
            long copies = Math.min(onHand, added);
            if (copies <= 0) {
                return;
            }
            PrintJob entry = PrintJob.create(
                    bbz, product.getName(), product.getSellingPrice().paise(),
                    confirmedMrpPaise(product), (int) copies, productId);
            entry.setStatus(REVIEW);
            entry.setOperatorName(operatorName);
            printJobs.save(entry);
            return;
        }
        // Keep the reviewer's count and add this command's entered quantity, never exceeding on hand.
        long copies = Math.min(onHand, Math.max(0, existing.getCopies()) + added);
        existing.setCopies((int) copies);
        existing.setBarcode(bbz);
        existing.setProductName(product.getName());
        existing.setSellingPricePaise(product.getSellingPrice().paise());
        existing.setMrpPaise(confirmedMrpPaise(product));
        if (operatorName != null && !operatorName.isBlank()) {
            existing.setOperatorName(operatorName); // the latest pricer
        }
        printJobs.save(existing); // same id — a mid-edit reviewer keeps their row
    }

    /** The label entries waiting for a reviewer, one per product. */
    @Transactional(readOnly = true)
    public List<LabelReviewEntry> reviewEntries() {
        return printJobs.findByStatusOrderByCreatedAtAsc(REVIEW).stream()
                .map(this::toReviewEntry)
                .toList();
    }

    private LabelReviewEntry toReviewEntry(PrintJob job) {
        Product p = job.getProductId() == null ? null : products.findById(job.getProductId()).orElse(null);
        UUID batchId = p == null ? null : labelBatchFor(p);
        String category = p == null ? "" : p.getCategory().code();
        long onHand = p == null ? 0 : stock.onHand(p.getId());
        return new LabelReviewEntry(
                job.getId(), job.getProductId(), batchId, job.getBarcode(), job.getProductName(),
                category, job.getSellingPricePaise(), job.getMrpPaise(), job.getCopies(), onHand,
                job.getOperatorName());
    }

    /**
     * A reviewer's edit of one waiting entry: the corrected name, category, price and MRP are written
     * back to the product (so the till agrees) and the entry is refreshed to match, with the copies
     * the reviewer chose. Stock is not touched — that was set at pricing.
     */
    @Transactional
    public void editReviewEntry(UUID jobId, LabelReviewEditRequest req) {
        PrintJob entry = printJobs.findById(jobId)
                .filter(j -> REVIEW.equals(j.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("no such review entry: " + jobId));
        UUID productId = entry.getProductId();
        Product product = products.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("no such product: " + productId));
        UUID batchId = labelBatchFor(product);
        // Write the corrected details back through the pricing path (MRP ceiling enforced, no stock
        // change since inHandQuantity is null). This re-enqueues nothing — enqueue is the caller's job.
        shelfPricing.saveExisting(new PriceExistingRequest(
                productId, batchId, req.categoryCode(), req.sellingPricePaise(), req.mrpPaise(),
                null, req.name(), false, null));
        // Update the entry in place (same id) with the reviewer's copies and the corrected details.
        Product edited = products.findById(productId).orElseThrow();
        entry.setCopies(Math.max(0, req.copies()));
        entry.setBarcode(bbzFor(edited));
        entry.setProductName(edited.getName());
        entry.setSellingPricePaise(edited.getSellingPrice().paise());
        entry.setMrpPaise(confirmedMrpPaise(edited));
        printJobs.save(entry);
    }

    /** Rejects a review entry: drop it from the queue without printing. The stock is untouched. */
    @Transactional
    public void rejectReviewEntry(UUID jobId) {
        PrintJob entry = printJobs.findById(jobId)
                .filter(j -> REVIEW.equals(j.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("no such review entry: " + jobId));
        printJobs.delete(entry);
    }

    /**
     * Sends every review entry to the print queue: each entry explodes into its {@code copies}
     * single-label queued jobs (so the hold-and-pair poller prints them two-up and spaced), and the
     * review row is cleared. Returns how many products and labels were sent.
     */
    @Transactional
    public QueueAwaitingResult sendAllForReview() {
        int productsQueued = 0;
        long labelsQueued = 0;
        for (PrintJob entry : printJobs.findByStatusOrderByCreatedAtAsc(REVIEW)) {
            int copies = explodeToQueue(entry);
            if (copies > 0) {
                productsQueued++;
                labelsQueued += copies;
            }
        }
        return new QueueAwaitingResult(productsQueued, labelsQueued);
    }

    /** Sends one review entry to the print queue — the reviewer approving a single product. */
    @Transactional
    public QueueAwaitingResult sendReviewEntry(UUID jobId) {
        PrintJob entry = printJobs.findById(jobId)
                .filter(j -> REVIEW.equals(j.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("no such review entry: " + jobId));
        int copies = explodeToQueue(entry);
        return new QueueAwaitingResult(copies > 0 ? 1 : 0, copies);
    }

    /** Explodes a review entry into its copies of single-label queued jobs and clears it. */
    private int explodeToQueue(PrintJob entry) {
        int copies = Math.max(0, entry.getCopies());
        for (int i = 0; i < copies; i++) {
            PrintJob job = PrintJob.create(
                    entry.getBarcode(), entry.getProductName(), entry.getSellingPricePaise(),
                    entry.getMrpPaise(), 1, entry.getProductId());
            // The review row is deleted below, so the queued jobs are where who-priced-it survives.
            job.setOperatorName(entry.getOperatorName());
            printJobs.save(job);
        }
        printJobs.delete(entry);
        return copies;
    }

    /**
     * Sends every priced product still awaiting a label to the print queue in one go — the
     * reviewer's single action. Each product queues one sticker per unit on hand (the count set at
     * pricing), as single-label jobs so the hold-and-pair executor prints them two-up and spaced.
     * A product already sitting in the queue is skipped, so pressing this twice does not double up;
     * a product with nothing on hand is skipped too. The poller marks each product labelled as its
     * stickers print, so it then drops off the awaiting list.
     */
    @Transactional
    public QueueAwaitingResult queueAllAwaiting() {
        Set<UUID> alreadyQueued = new HashSet<>();
        for (PrintJob job : printJobs.findByStatusOrderByCreatedAtAsc("queued")) {
            if (job.getProductId() != null) alreadyQueued.add(job.getProductId());
        }
        for (PrintJob job : printJobs.findByStatusOrderByCreatedAtAsc("printing")) {
            if (job.getProductId() != null) alreadyQueued.add(job.getProductId());
        }

        int productsQueued = 0;
        long labelsQueued = 0;
        for (Product p : products.findBySellingPriceIsNotNullAndLabelPrintedAtIsNullOrderByName()) {
            if (alreadyQueued.contains(p.getId())) {
                continue;
            }
            long qty = stock.onHand(p.getId());
            String bbz = bbzFor(p);
            if (qty <= 0 || bbz.isEmpty()) {
                continue;
            }
            Long mrp = confirmedMrpPaise(p);
            long price = p.getSellingPrice().paise();
            for (long i = 0; i < qty; i++) {
                printJobs.save(PrintJob.create(bbz, p.getName(), price, mrp, 1, p.getId()));
            }
            productsQueued++;
            labelsQueued += qty;
        }
        return new QueueAwaitingResult(productsQueued, labelsQueued);
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
                confirmedMrpPaise(product),
                stock.onHand(product.getId()),
                labelBatchFor(product),
                product.getCategory().code());
    }

    /** The batch a review edit reconciles against — the product's good stock, newest first. */
    private UUID labelBatchFor(Product product) {
        List<Batch> found = batches.findByProductIdNewestFirst(product.getId());
        return found.stream()
                .filter(b -> b.getCondition() == com.bahikhaata.contracts.StockCondition.GOOD
                        && b.getQuantityReceived() > 0)
                .findFirst()
                .or(() -> found.stream().findFirst())
                .map(Batch::getId)
                .orElse(null);
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
