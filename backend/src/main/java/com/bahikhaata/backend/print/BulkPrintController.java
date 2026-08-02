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
import com.bahikhaata.contracts.BulkPrintRequest;
import com.bahikhaata.contracts.BulkPrintResult;
import com.bahikhaata.contracts.LabelReviewEditRequest;
import com.bahikhaata.contracts.LabelReviewEntry;
import com.bahikhaata.contracts.QueueAwaitingResult;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The bulk label screen: list products awaiting a label, and print a chosen set of them. */
@RestController
@RequestMapping("/api/print-jobs/bulk")
public class BulkPrintController {

    private final BulkLabelPrint bulkLabelPrint;

    public BulkPrintController(BulkLabelPrint bulkLabelPrint) {
        this.bulkLabelPrint = bulkLabelPrint;
    }

    @GetMapping("/awaiting")
    public List<AwaitingLabelProduct> awaiting() {
        return bulkLabelPrint.awaitingLabel();
    }

    /** The reviewer's one action: send every awaiting product to the spaced print queue. */
    @PostMapping("/queue-awaiting")
    public QueueAwaitingResult queueAwaiting() {
        return bulkLabelPrint.queueAllAwaiting();
    }

    @PostMapping
    public BulkPrintResult printBulk(@RequestBody BulkPrintRequest req) {
        return bulkLabelPrint.printBulk(req.productIds());
    }

    // --- review queue: one entry per product, edited then sent to the print queue ---

    /** The label entries waiting for a reviewer, one row per product. */
    @GetMapping("/review")
    public List<LabelReviewEntry> reviewEntries() {
        return bulkLabelPrint.reviewEntries();
    }

    /** Apply a reviewer's edit (name/category/price/MRP/copies) to one waiting entry. */
    @PutMapping("/review/{jobId}")
    public ResponseEntity<Void> editReview(
            @PathVariable UUID jobId, @RequestBody LabelReviewEditRequest req) {
        bulkLabelPrint.editReviewEntry(jobId, req);
        return ResponseEntity.noContent().build();
    }

    /** Reject one review entry — drop it from the queue without printing. */
    @DeleteMapping("/review/{jobId}")
    public ResponseEntity<Void> rejectReview(@PathVariable UUID jobId) {
        bulkLabelPrint.rejectReviewEntry(jobId);
        return ResponseEntity.noContent().build();
    }

    /** Send every review entry to the print queue in one go. */
    @PostMapping("/review/send")
    public QueueAwaitingResult sendReview() {
        return bulkLabelPrint.sendAllForReview();
    }

    /** Send one review entry to the print queue — approve a single product. */
    @PostMapping("/review/{jobId}/send")
    public QueueAwaitingResult sendReviewOne(@PathVariable UUID jobId) {
        return bulkLabelPrint.sendReviewEntry(jobId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<String> conflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(e.getMessage());
    }
}
