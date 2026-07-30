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

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.PrintLabelRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the print job queue every 500ms and executes queued jobs.
 *
 * <p>Each job is self-contained: its label is rendered from the fields it carries, with no
 * database read. On success the job is marked "done" and — if it references a product — that
 * product's label is marked printed. On printer unreachable, retries up to 10 times (~5 seconds);
 * after 10 retries, marks "failed". On other errors, marks "failed" immediately.
 */
@Service
public class PrintExecutorService {
    private static final Logger log = LoggerFactory.getLogger(PrintExecutorService.class);
    private static final int MAX_RETRIES = 10;
    private static final int BATCH_SIZE = 5;

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

    @Scheduled(fixedRate = 500)
    @Transactional
    public void executePrintQueue() {
        List<PrintJob> queued = printJobRepository
            .findByStatusOrderByCreatedAtAsc("queued")
            .stream()
            .limit(BATCH_SIZE)
            .toList();

        for (PrintJob job : queued) {
            executeSingleJob(job);
        }
    }

    private void executeSingleJob(PrintJob job) {
        try {
            job.setStatus("printing");
            printJobRepository.save(job);

            // Render straight from the job's own fields — no database read.
            String tspl = labelService.renderLabel(labelRequestFrom(job));

            // Send to printer. The stock is 2-up — one rendered document is a row of two identical
            // stickers — so the asked-for copies convert to rows, rounded up: an odd ask yields one
            // extra usable sticker, never a blank one.
            printerDriver.sendLabel(tspl, LabelTemplateService.rowsFor(job.getCopies()));

            // Success
            job.setStatus("done");
            job.setError(null);
            job.setRetryCount(0);
            markProductLabelled(job);
            log.info("Job {} printed successfully", job.getId());

        } catch (PrinterDriver.PrinterException e) {
            if (isPrinterOfflineError(e)) {
                // Printer unreachable — retry later
                if (job.getRetryCount() < MAX_RETRIES) {
                    job.setStatus("queued");
                    job.incrementRetry();
                    log.warn("Job {} printer offline, retry {} of {}", job.getId(), job.getRetryCount(), MAX_RETRIES);
                } else {
                    job.setStatus("failed");
                    job.setError("Printer unreachable after " + MAX_RETRIES + " retries: " + e.getMessage());
                    log.error("Job {} failed after max retries: {}", job.getId(), e.getMessage());
                }
            } else {
                // Other error (template, rendering, etc.) — fail immediately
                job.setStatus("failed");
                job.setError("Printer error: " + e.getMessage());
                log.error("Job {} failed: {}", job.getId(), e.getMessage());
            }
        } catch (Exception e) {
            job.setStatus("failed");
            job.setError("Unexpected error: " + e.getMessage());
            log.error("Job {} unexpected error: {}", job.getId(), e);
        } finally {
            printJobRepository.save(job);
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
