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

import com.bahikhaata.contracts.QueuePrintJobRequest;
import com.bahikhaata.contracts.QueuePrintJobResponse;
import com.bahikhaata.contracts.PrintJobStatusResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public PrintController(PrintJobRepository printJobRepository) {
        this.printJobRepository = printJobRepository;
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

        PrintJob job = PrintJob.create(
                req.barcode(),
                req.productName(),
                req.sellingPricePaise(),
                req.mrpPaise(),
                req.copies(),
                req.productId());
        printJobRepository.save(job);

        QueuePrintJobResponse resp = new QueuePrintJobResponse(
            job.getId(),
            job.getStatus(),
            job.getBarcode(),
            job.getProductName(),
            job.getCopies(),
            job.getError());

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
