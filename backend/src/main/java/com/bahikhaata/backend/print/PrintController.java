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

import com.bahikhaata.contracts.AwaitingLabelProduct;
import com.bahikhaata.contracts.QueuePrintJobRequest;
import com.bahikhaata.contracts.QueuePrintJobResponse;
import com.bahikhaata.contracts.PrintJobStatusResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Print job endpoints: queue a label print and poll job status.
 *
 * <p>POST /api/print-jobs queues a job immediately and returns jobId.
 * GET /api/print-jobs/{jobId} polls the job status until done or failed.
 */
@RestController
@RequestMapping("/api/print-jobs")
public class PrintController {
    private final PrintJobRepository printJobRepository;
    private final PrintExecutorService executor;
    private final BulkLabelPrint labels;

    public PrintController(
            PrintJobRepository printJobRepository, PrintExecutorService executor, BulkLabelPrint labels) {
        this.printJobRepository = printJobRepository;
        this.executor = executor;
        this.labels = labels;
    }

    /**
     * Look up a priced product by any of its barcodes for the reprint screen: the shelf BBZ, or the
     * original LSN/ASIN. Returns the label figures (name, price, confirmed MRP). An unknown code is
     * a 400 and an unpriced product a 409, each with a message the screen shows.
     */
    @GetMapping("/label-for")
    public AwaitingLabelProduct labelFor(@RequestParam String barcode) {
        return labels.labelByBarcode(barcode);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<String> conflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(e.getMessage());
    }

    /** How many labels are held, waiting for a partner to pair with — shown on the pricing screen. */
    @GetMapping("/pending-count")
    public java.util.Map<String, Long> pendingCount() {
        return java.util.Map.of("count", executor.pendingCount());
    }

    /** Print the held labels now — a lone leftover goes out as a duplicate pair. */
    @PostMapping("/flush")
    public java.util.Map<String, Long> flush() {
        return java.util.Map.of("printed", executor.flushPending());
    }

    @PostMapping
    public ResponseEntity<QueuePrintJobResponse> queuePrintJob(@RequestBody QueuePrintJobRequest req) {
        if (req.barcode() == null || req.barcode().isBlank()
                || req.productName() == null || req.productName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (req.copies() < 1 || req.copies() > 100) {
            return ResponseEntity.badRequest().build();
        }

        // One sticker per copy: queue N single-label jobs, not one job of N. The hold-and-pair
        // executor pairs jobs two-up and ignores a job's copies count, so N jobs are what turn
        // into N labels (ceil(N/2) rows). Return the first job; the response reports N queued.
        PrintJob first = null;
        for (int i = 0; i < req.copies(); i++) {
            PrintJob job = PrintJob.create(
                    req.barcode(),
                    req.productName(),
                    req.sellingPricePaise(),
                    req.mrpPaise(),
                    1,
                    req.productId());
            printJobRepository.save(job);
            if (first == null) {
                first = job;
            }
        }

        QueuePrintJobResponse resp = new QueuePrintJobResponse(
            first.getId(),
            first.getStatus(),
            first.getBarcode(),
            first.getProductName(),
            req.copies(),
            first.getError());

        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<PrintJobStatusResponse> getJobStatus(@PathVariable UUID jobId) {
        return printJobRepository.findById(jobId)
            .map(job -> new PrintJobStatusResponse(
                job.getId(),
                job.getStatus(),
                job.getBarcode(),
                job.getProductName(),
                job.getCopies(),
                job.getError(),
                job.getCreatedAt(),
                job.getUpdatedAt()))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
