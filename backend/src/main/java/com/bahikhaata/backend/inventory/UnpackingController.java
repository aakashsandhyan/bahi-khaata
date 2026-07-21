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

import com.bahikhaata.contracts.Money;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Opening cartons and recording what was in them.
 *
 * <p>Shaped for the screen rather than for the schema: the terminal asks about a carton and
 * the things inside it, never about batches or ledger entries. The vocabulary here is
 * deliberately the shop's.
 */
@RestController
@RequestMapping("/api/unpacking")
class UnpackingController {

    private final GoodsInCounting counting;
    private final LotClosing closing;

    UnpackingController(GoodsInCounting counting, LotClosing closing) {
        this.counting = counting;
        this.closing = closing;
    }

    /** Every carton in a delivery, and where each has got to. */
    @GetMapping("/lots/{lotId}/boxes")
    List<GoodsInCounting.BoxProgress> boxesOf(@PathVariable UUID lotId) {
        return counting.progressOf(lotId);
    }

    /** What should be in one carton — the screen shown after scanning the box. */
    @GetMapping("/boxes/{boxId}/lines")
    List<GoodsInCounting.LineToFind> linesOf(@PathVariable UUID boxId) {
        return counting.linesIn(boxId);
    }

    /** Finds the carton by the number printed on it, which is what the scanner reads. */
    @GetMapping("/boxes/by-tracking/{trackingNumber}")
    ResponseEntity<List<GoodsInCounting.CartonFound>> byTracking(
            @PathVariable String trackingNumber) {
        List<GoodsInCounting.CartonFound> found = counting.findByTracking(trackingNumber);
        return found.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(found);
    }

    @PostMapping("/lines/{lineId}/count")
    GoodsInCounting.CountOutcome count(
            @PathVariable UUID lineId, @RequestBody CountRequest request) {
        return counting.countExpected(
                lineId,
                request.quantity(),
                request.mrpPaise() == null ? null : Money.ofPaise(request.mrpPaise()),
                request.mrpIsEstimate(),
                Instant.now());
    }

    /** Something in the carton that no line names. */
    @PostMapping("/boxes/{boxId}/unlisted")
    GoodsInCounting.CountOutcome unlisted(
            @PathVariable UUID boxId, @RequestBody UnlistedRequest request) {
        return counting.countUnlisted(
                boxId,
                request.code(),
                request.name(),
                request.categoryCode(),
                request.quantity(),
                request.mrpPaise() == null ? null : Money.ofPaise(request.mrpPaise()),
                request.mrpIsEstimate(),
                Instant.now());
    }

    @PostMapping("/boxes/{boxId}/finish")
    ResponseEntity<Void> finish(@PathVariable UUID boxId) {
        counting.finishBox(boxId, Instant.now());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/boxes/{boxId}/reopen")
    ResponseEntity<Void> reopen(@PathVariable UUID boxId) {
        counting.reopenBox(boxId);
        return ResponseEntity.noContent().build();
    }

    /** Which cartons nobody has opened — asked before closing, so the answer is not a surprise. */
    @GetMapping("/lots/{lotId}/unopened")
    List<String> unopened(@PathVariable UUID lotId) {
        return closing.unopenedCartons(lotId);
    }

    /**
     * Finishes a delivery and settles what it cost.
     *
     * <p>{@code confirm=true} is required only when cartons remain unopened, and the refusal
     * names them. Closing is not blocked by them: goods that never arrived would otherwise hold
     * a delivery open forever and nothing in it could be priced.
     */
    @PostMapping("/lots/{lotId}/close")
    LotClosing.ClosingOutcome closeLot(
            @PathVariable UUID lotId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false")
                    boolean confirm) {
        return closing.close(lotId, confirm, Instant.now());
    }

    /** Unopened cartons are a decision to be taken, not a validation error. */
    @ExceptionHandler(LotClosing.UnopenedCartonsException.class)
    ResponseEntity<UnopenedResponse> unopenedCartons(LotClosing.UnopenedCartonsException e) {
        return ResponseEntity.status(409)
                .body(new UnopenedResponse(e.getMessage(), e.getTrackingNumbers()));
    }

    record UnopenedResponse(String message, List<String> trackingNumbers) {}

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    /** A closed lot is not an operator error to retry, so it is reported as a conflict. */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<String> conflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(e.getMessage());
    }

    record CountRequest(long quantity, Long mrpPaise, boolean mrpIsEstimate) {}

    record UnlistedRequest(
            String code,
            String name,
            String categoryCode,
            long quantity,
            Long mrpPaise,
            boolean mrpIsEstimate) {}
}
