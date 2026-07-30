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
package com.bahikhaata.backend.pricing;

import com.bahikhaata.contracts.ApproveCaptureRequest;
import com.bahikhaata.contracts.CaptureSummary;
import com.bahikhaata.contracts.ShelfPricedProduct;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The desktop review queue: list pending captures, approve one into a shelf product, or reject. */
@RestController
@RequestMapping("/api/pricing/review-queue")
public class ReviewQueueController {

    private final CaptureService captureService;

    public ReviewQueueController(CaptureService captureService) {
        this.captureService = captureService;
    }

    @GetMapping
    public List<CaptureSummary> pending() {
        return captureService.pending();
    }

    @PostMapping("/{captureId}/approve")
    public ShelfPricedProduct approve(
            @PathVariable UUID captureId, @RequestBody ApproveCaptureRequest req) {
        return captureService.approve(captureId, req);
    }

    @PostMapping("/{captureId}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID captureId) {
        captureService.reject(captureId);
        return ResponseEntity.noContent().build();
    }
}
