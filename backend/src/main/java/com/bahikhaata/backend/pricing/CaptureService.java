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
package com.bahikhaata.backend.pricing;

import com.bahikhaata.contracts.ApproveCaptureRequest;
import com.bahikhaata.contracts.CaptureRequest;
import com.bahikhaata.contracts.CaptureSummary;
import com.bahikhaata.contracts.PriceManualRequest;
import com.bahikhaata.contracts.ShelfPricedProduct;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The mobile capture queue and its review. Captures are pricing-free drafts keyed from a phone;
 * approval is the only path from a capture to a shelf product, and it runs the same manual save as
 * the pricing workbench so a reviewer-completed capture and a hand-keyed workbench product are the
 * same thing.
 */
@Service
public class CaptureService {

    private final ProductCaptureRepository captures;
    private final ShelfPricing shelfPricing;

    public CaptureService(ProductCaptureRepository captures, ShelfPricing shelfPricing) {
        this.captures = captures;
        this.shelfPricing = shelfPricing;
    }

    /** Records a pending capture. Carries no price — pricing is decided at review. */
    @Transactional
    public CaptureSummary capture(CaptureRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("a capture needs a name");
        }
        ProductCapture saved = captures.save(
                new ProductCapture(req.name(), req.mrpPaise(), req.description(), req.lotId()));
        return toSummary(saved);
    }

    /** The review queue: pending captures, oldest first. */
    @Transactional(readOnly = true)
    public List<CaptureSummary> pending() {
        return captures.findByStatusOrderByCreatedAtAsc(ProductCapture.PENDING).stream()
                .map(CaptureService::toSummary)
                .toList();
    }

    /**
     * Completes a capture into a shelf product: runs the manual pricing save with the reviewer's
     * lot/category/price and the capture's name, then marks the capture approved. The reviewer's
     * MRP wins where given, otherwise the captured one is carried through.
     */
    @Transactional
    public ShelfPricedProduct approve(UUID captureId, ApproveCaptureRequest req) {
        ProductCapture capture = captures.findById(captureId)
                .orElseThrow(() -> new IllegalArgumentException("no such capture: " + captureId));
        Long mrp = req.mrpPaise() != null ? req.mrpPaise() : capture.getMrpPaise();

        ShelfPricedProduct product = shelfPricing.saveManual(new PriceManualRequest(
                req.lotId(),
                capture.getName(),
                req.categoryCode(),
                req.condition(),
                req.quantity(),
                req.sellingPricePaise(),
                mrp,
                null, // capture approval records no operator name
                null)); // and no bin — captured stock has none to carry over

        capture.approve();
        captures.save(capture);
        return product;
    }

    /** Rejects a pending capture; no product is created. */
    @Transactional
    public void reject(UUID captureId) {
        ProductCapture capture = captures.findById(captureId)
                .orElseThrow(() -> new IllegalArgumentException("no such capture: " + captureId));
        capture.reject();
        captures.save(capture);
    }

    private static CaptureSummary toSummary(ProductCapture c) {
        return new CaptureSummary(
                c.getId(), c.getName(), c.getMrpPaise(), c.getDescription(),
                c.getLotId(), c.getStatus(), c.getCreatedAt());
    }
}
