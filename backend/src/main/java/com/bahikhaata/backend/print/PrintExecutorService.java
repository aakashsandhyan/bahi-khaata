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

import com.bahikhaata.contracts.PrintLabelRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bahikhaata.backend.catalog.ProductRepository;

/**
 * The label print queue, paired for the 2-up roll.
 *
 * <p>Each queued job is one self-contained label — one column of a row. The stickers come two to a
 * row, so a label cannot print alone without either wasting the other sticker or printing a
 * duplicate; instead a lone label is <em>held</em> and printed only once a second label pairs with
 * it, at which point the two go out on one row (two different products, no waste). At rest at most
 * one label is ever held — the odd one waiting for a partner.
 *
 * <p>{@link #flushPending} prints a held leftover deliberately, as a duplicate pair, when the
 * operator is done and wants it out anyway. Labels render from the job's own fields, with no
 * database read; a printed job marks its product labelled.
 */
@Service
public class PrintExecutorService {
    private static final Logger log = LoggerFactory.getLogger(PrintExecutorService.class);
    private static final int MAX_RETRIES = 10;
    private static final int MAX_PAIRS_PER_POLL = 5;

    private final PrintJobRepository printJobRepository;
    private final PrinterDriver printerDriver;
    private final LabelTemplateService labelService;
    private final ProductRepository productRepository;

    public PrintExecutorService(
        PrintJobRepository printJobRepository,
        PrinterDriver printerDriver,
        LabelTemplateService labelService,
        ProductRepository productRepository) {
        this.printJobRepository = printJobRepository;
        this.printerDriver = printerDriver;
        this.labelService = labelService;
        this.productRepository = productRepository;
    }

    /** How many labels are held, waiting to print — the pending count the pricing screen shows. */
    @Transactional(readOnly = true)
    public long pendingCount() {
        return printJobRepository.findByStatusOrderByCreatedAtAsc("queued").size();
    }

    /**
     * Every poll, print the held labels in pairs. A lone last label is left queued — held for a
     * partner — rather than printed alone.
     */
    @Scheduled(fixedRate = 500)
    @Transactional
    public void executePrintQueue() {
        List<PrintJob> queued = printJobRepository.findByStatusOrderByCreatedAtAsc("queued");
        int pairs = Math.min(queued.size() / 2, MAX_PAIRS_PER_POLL);
        for (int i = 0; i < pairs; i++) {
            printRow(queued.get(2 * i), queued.get(2 * i + 1));
        }
    }

    /**
     * Prints every held label now, deliberately — pairs first, then a lone leftover as a duplicate
     * pair rather than a blank. Returns how many labels were sent out. Used by the flush control
     * when the operator is done and wants pending labels printed even though one is unpaired.
     */
    @Transactional
    public long flushPending() {
        List<PrintJob> queued = printJobRepository.findByStatusOrderByCreatedAtAsc("queued");
        long printed = 0;
        int i = 0;
        for (; i + 1 < queued.size(); i += 2) {
            if (printRow(queued.get(i), queued.get(i + 1))) {
                printed += 2;
            }
        }
        if (i < queued.size()) {
            // The odd leftover: print it as a duplicate pair so no sticker is left blank.
            PrintJob lone = queued.get(i);
            if (printOne(lone)) {
                printed += 1;
            }
        }
        return printed;
    }

    /** Prints two labels as one row; marks both done and their products on success. */
    private boolean printRow(PrintJob left, PrintJob right) {
        return send(labelService.renderRow(labelRequestFrom(left), labelRequestFrom(right)), left, right);
    }

    /** Prints one label as a duplicate pair — both columns the same product. */
    private boolean printOne(PrintJob job) {
        return send(labelService.renderLabel(labelRequestFrom(job)), job);
    }

    /** Sends a rendered row and settles the jobs it carries: done on success, retry/fail on error. */
    private boolean send(String tspl, PrintJob... jobs) {
        for (PrintJob job : jobs) {
            job.setStatus("printing");
            printJobRepository.save(job);
        }
        try {
            printerDriver.sendLabel(tspl, 1);
            for (PrintJob job : jobs) {
                job.setStatus("done");
                job.setError(null);
                job.setRetryCount(0);
                markProductLabelled(job);
                printJobRepository.save(job);
            }
            return true;
        } catch (PrinterDriver.PrinterException e) {
            boolean offline = isPrinterOfflineError(e);
            for (PrintJob job : jobs) {
                if (offline && job.getRetryCount() < MAX_RETRIES) {
                    job.setStatus("queued");
                    job.incrementRetry();
                } else {
                    job.setStatus("failed");
                    job.setError((offline ? "Printer unreachable: " : "Printer error: ") + e.getMessage());
                }
                printJobRepository.save(job);
            }
            log.warn("Print row failed ({}): {}", offline ? "offline" : "error", e.getMessage());
            return false;
        }
    }

    /** The label data the job carries — the whole point of a self-contained job. */
    private PrintLabelRequest labelRequestFrom(PrintJob job) {
        return new PrintLabelRequest(
                job.getBarcode(), job.getProductName(), job.getMrpPaise(), job.getSellingPricePaise());
    }

    /** Stamp the product's label-printed marker, if this job references one. */
    private void markProductLabelled(PrintJob job) {
        if (job.getProductId() == null) {
            return;
        }
        productRepository.findById(job.getProductId()).ifPresent(product -> {
            product.markLabelPrinted(Instant.now());
            productRepository.save(product);
        });
    }

    private boolean isPrinterOfflineError(PrinterDriver.PrinterException e) {
        String msg = e.getMessage().toLowerCase();
        return msg.contains("unreachable")
            || msg.contains("timeout")
            || msg.contains("not connected")
            || msg.contains("connection");
    }
}
