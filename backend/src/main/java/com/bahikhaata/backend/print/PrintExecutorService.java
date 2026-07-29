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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the print job queue every 500ms and executes queued jobs.
 *
 * <p>For each queued job: renders the label (FreeMarker → ZPL), sends to printer via
 * PrinterDriver. On success, marks as "done". On printer unreachable, retries up to 10
 * times (~5 seconds); after 10 retries, marks as "failed" with error message.
 * On other errors (template, item not found), marks "failed" immediately.
 */
@Service
public class PrintExecutorService {
    private static final Logger log = LoggerFactory.getLogger(PrintExecutorService.class);
    private static final int MAX_RETRIES = 10;
    private static final int BATCH_SIZE = 5;

    private final PrintJobRepository printJobRepository;
    private final PrinterDriver printerDriver;
    private final LabelTemplateService labelService;

    public PrintExecutorService(
        PrintJobRepository printJobRepository,
        PrinterDriver printerDriver,
        LabelTemplateService labelService) {
        this.printJobRepository = printJobRepository;
        this.printerDriver = printerDriver;
        this.labelService = labelService;
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

            // Render label via FreeMarker
            PrintLabelRequest labelReq = buildLabelRequest(job);
            String zpl = labelService.renderLabel(labelReq);

            // Send to printer
            printerDriver.sendLabel(zpl, job.getCopies());

            // Success
            job.setStatus("done");
            job.setError(null);
            job.setRetryCount(0);
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

    private PrintLabelRequest buildLabelRequest(PrintJob job) throws PrinterDriver.PrinterException {
        // TODO: fetch item (Box, Batch, Product) by job.itemId and build label data
        // For now, return a placeholder; real implementation loads from repositories
        throw new PrinterDriver.PrinterException("Label data building not yet implemented");
    }

    private boolean isPrinterOfflineError(PrinterDriver.PrinterException e) {
        String msg = e.getMessage().toLowerCase();
        return msg.contains("unreachable")
            || msg.contains("timeout")
            || msg.contains("not connected")
            || msg.contains("connection");
    }
}
