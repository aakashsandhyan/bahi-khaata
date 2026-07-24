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

import com.bahikhaata.contracts.BacklogItem;
import com.bahikhaata.contracts.ExtraRecord;
import com.bahikhaata.contracts.IssueTypeOption;
import com.bahikhaata.contracts.ProductStates;
import com.bahikhaata.contracts.ProductSummary;
import com.bahikhaata.contracts.ReviewItem;
import com.bahikhaata.contracts.ShortLine;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
 * Preparing imperfect goods, and moving units between the states they can be held in.
 *
 * <p>The vocabulary is the shop's — clean, repair, rebuild, ready, scrap — not the schema's. The
 * kinds of work offered depend on the department; the moves are recorded through the remediation
 * service so the ledger stays append-only.
 */
@RestController
@RequestMapping("/api/remediation")
class RemediationController {

    private final GoodsRemediation remediation;

    RemediationController(GoodsRemediation remediation) {
        this.remediation = remediation;
    }

    /** The kinds of work a department offers, for the picker when marking an item needs-work. */
    @GetMapping("/issue-types")
    List<IssueTypeOption> issueTypes(@RequestParam String category) {
        return remediation.issueTypesFor(category);
    }

    /** A product and every state its stock is held in, for the screen that moves units between them. */
    @GetMapping("/products/{productId}/states")
    ProductStates states(@PathVariable UUID productId) {
        return remediation.statesOf(productId);
    }

    /** The states a scanned code's product is held in — a rescue reached straight from a scan. */
    @GetMapping("/resolve")
    ProductStates resolve(@RequestParam String code) {
        return remediation.statesByCode(code);
    }

    /** Products whose name matches, for looking one up by search rather than scan. */
    @GetMapping("/search")
    List<ProductSummary> search(@RequestParam String q) {
        return remediation.search(q);
    }

    /** The needs-work backlog: every pile waiting on preparation, grouped by the UI. */
    @GetMapping("/backlog")
    List<BacklogItem> backlog() {
        return remediation.backlog();
    }

    /** Damaged and broken goods with their notes, for sorting the fixable into needs-work. */
    @GetMapping("/review")
    List<ReviewItem> review() {
        return remediation.reviewList();
    }

    /** Extras recorded against boxes, still held as their own product, waiting to be linked. */
    @GetMapping("/extras")
    List<ExtraRecord> extras() {
        return remediation.extras();
    }

    /** The lines of a delivery still missing units — candidates an extra can be linked to fill. */
    @GetMapping("/lots/{lotId}/shorts")
    List<ShortLine> shorts(@PathVariable UUID lotId) {
        return remediation.shortsInLot(lotId);
    }

    /** Reattribute an extra to the real product it turned out to be: the two are one, merged. */
    @PostMapping("/link")
    ResponseEntity<Void> link(@RequestBody LinkRequest request) {
        remediation.linkExtra(request.extraProductId(), request.targetLineId(), Instant.now());
        return ResponseEntity.noContent().build();
    }

    record LinkRequest(UUID extraProductId, UUID targetLineId) {}

    /** Moves a quantity of a product's units from one state to another within an open lot. */
    @PostMapping("/change-state")
    ResponseEntity<Void> changeState(@RequestBody ChangeStateRequest request) {
        remediation.changeState(
                request.productId(),
                request.lotId(),
                request.from(),
                request.fromIssueType(),
                request.to(),
                request.toIssueType(),
                request.quantity(),
                Instant.now());
        return ResponseEntity.noContent().build();
    }

    record ChangeStateRequest(
            UUID productId,
            UUID lotId,
            StockCondition from,
            String fromIssueType,
            StockCondition to,
            String toIssueType,
            long quantity) {}

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<String> conflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(e.getMessage());
    }
}
