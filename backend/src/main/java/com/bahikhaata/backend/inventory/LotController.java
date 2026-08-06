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
package com.bahikhaata.backend.inventory;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.AddProductRequest;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CreateManualLotRequest;
import com.bahikhaata.contracts.LotCostReconciliation;
import com.bahikhaata.contracts.LotIntakeStats;
import com.bahikhaata.contracts.LotLineResponse;
import com.bahikhaata.contracts.LotResponse;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.ReceiveLotRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receiving deliveries.
 *
 * <p>The response returns what each line was costed at, so the operator can see the allocation
 * rather than take it on trust — a figure nobody looked at is a figure nobody catches.
 */
@RestController
@RequestMapping("/api/lots")
class LotController {

    private final GoodsInService goodsIn;
    private final LotRepository lotRepository;
    private final BoxReceiptRepository boxReceiptRepository;
    private final ExpectedLineRepository expectedLineRepository;
    private final ProductRepository productRepository;
    private final BoxRepository boxRepository;
    private final SupplierService supplierService;
    private final LotCategoryResolver lotCategories;
    private final BatchRepository batchRepository;
    private final LotClosing lotClosing;

    LotController(
            GoodsInService goodsIn,
            LotRepository lotRepository,
            BoxReceiptRepository boxReceiptRepository,
            ExpectedLineRepository expectedLineRepository,
            ProductRepository productRepository,
            BoxRepository boxRepository,
            SupplierService supplierService,
            LotCategoryResolver lotCategories,
            BatchRepository batchRepository,
            LotClosing lotClosing) {
        this.goodsIn = goodsIn;
        this.lotRepository = lotRepository;
        this.boxReceiptRepository = boxReceiptRepository;
        this.expectedLineRepository = expectedLineRepository;
        this.productRepository = productRepository;
        this.boxRepository = boxRepository;
        this.supplierService = supplierService;
        this.lotCategories = lotCategories;
        this.batchRepository = batchRepository;
        this.lotClosing = lotClosing;
    }

    @GetMapping
    ResponseEntity<List<LotSummaryDto>> listLots() {
        Map<UUID, String> categoryByLot = lotCategories.categoryByLot();
        List<LotSummaryDto> results = lotRepository.findAll().stream()
            .filter(Lot::isOpen)
            .map(lot -> {
                List<BoxReceipt> boxes = boxReceiptRepository.findByLotId(lot.getId());
                long expected = boxes.size();
                long received = boxReceiptRepository.countByLotIdAndState(lot.getId(), com.bahikhaata.contracts.BoxState.RECEIVED);
                long unpacked = boxReceiptRepository.countByLotIdAndState(lot.getId(), com.bahikhaata.contracts.BoxState.UNPACKED);
                long rejected = boxReceiptRepository.countByLotIdAndState(lot.getId(), com.bahikhaata.contracts.BoxState.REJECTED);
                long notReceived = boxReceiptRepository.countByLotIdAndState(lot.getId(), com.bahikhaata.contracts.BoxState.NOT_RECEIVED);
                return new LotSummaryDto(lot.getId(), lot.getSupplier(), lot.getReceivedOn(), lot.isReceivingComplete(),
                    lot.isManual(), categoryByLot.get(lot.getId()), expected, received, unpacked, rejected, notReceived);
            })
            .sorted(Comparator
                .comparing((LotSummaryDto l) -> l.receivingComplete())
                .thenComparing(Comparator.comparing((LotSummaryDto l) -> l.id()).reversed()))
            .toList();
        return ResponseEntity.ok(results);
    }

    @PostMapping
    ResponseEntity<LotResponse> receive(@RequestBody ReceiveLotRequest request) {
        GoodsInService.ReceivedLot received = goodsIn.receive(request);

        List<LotLineResponse> lines = new ArrayList<>(received.batches().size());
        for (Batch batch : received.batches()) {
            lines.add(
                    new LotLineResponse(
                            batch.getId().toString(),
                            batch.getProduct().getId().toString(),
                            batch.getQuantityReceived(),
                            batch.getQuantityDamaged(),
                            batch.getAllocatedTotal().paise(),
                            batch.getAllocatedUnitCost().paise(),
                            batch.getCostBasis()));
        }

        Lot lot = received.lot();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        new LotResponse(
                                lot.getId().toString(),
                                lot.getSupplier(),
                                lot.getReceivedOn().toString(),
                                lot.getAmountPaid().paise(),
                                lot.getFreight().paise(),
                                received.allocation().totalAllocated().paise(),
                                lot.getAllocationMethod(),
                                lines));
    }

    @PostMapping("/manual")
    ResponseEntity<LotSummaryDto> createManualLot(@RequestBody CreateManualLotRequest request) {
        LocalDate receivedOn = LocalDate.parse(request.receivedOn());
        Supplier supplier = supplierService.resolveActiveSupplier(request.supplierId());
        Lot lot = new Lot(
                supplier,
                receivedOn,
                com.bahikhaata.contracts.Money.ofPaise(request.amountPaidPaise()),
                com.bahikhaata.contracts.Money.ZERO,
                request.allocationMethod(),
                true);
        lot = lotRepository.save(lot);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LotSummaryDto(lot.getId(), lot.getSupplier(), lot.getReceivedOn(),
                        lot.isReceivingComplete(), lot.isManual(), null, 0, 0, 0, 0, 0));
    }

    /**
     * Marks a lot's receiving finished by hand. A manifest-backed lot completes on its own when
     * its last box goes terminal; a manual lot has no such event, so without this action it sits
     * in the dashboard's still-receiving alert forever with nothing anyone can do about it. This
     * sets the same fact the automatic path sets — nothing else: the lot stays open, stock is
     * untouched.
     */
    @PostMapping("/{lotId}/receiving-complete")
    ResponseEntity<?> markReceivingComplete(@PathVariable UUID lotId) {
        Lot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new IllegalArgumentException("lot not found"));
        if (!lot.isOpen()) {
            throw new IllegalArgumentException("lot is closed");
        }
        if (lot.isReceivingComplete()) {
            throw new IllegalArgumentException("receiving is already complete");
        }
        lot.setReceivingComplete(true);
        lotRepository.save(lot);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{lotId}/add-product")
    ResponseEntity<?> addProductToLot(
            @PathVariable UUID lotId,
            @RequestBody AddProductRequest request) {
        Lot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new IllegalArgumentException("lot not found"));

        if (!lot.isManual()) {
            throw new IllegalArgumentException("lot is not manual");
        }

        // Create new product for manual entry
        Product product = new Product(
                request.name(),
                Category.of(request.categoryCode()),
                java.util.Map.of("manualEntry", "true"));
        product = productRepository.save(product);

        // Create synthetic box for this manual product
        String boxTracking = "MANUAL-" + lot.getId() + "-" + (System.nanoTime() % 10000);
        Box box = new Box(lot, boxTracking);
        box = boxRepository.save(box);

        // Create expected line
        ExpectedLine line = new ExpectedLine(
                lot,
                box,
                product,
                request.code() != null && !request.code().isBlank() ? request.code() : product.getId().toString(),
                request.quantity(),
                request.estimatedCostPaise() != null ? Money.ofPaise(request.estimatedCostPaise()) : null);
        expectedLineRepository.save(line);

        // Calculate totals
        List<ExpectedLine> allLines = expectedLineRepository.findByLotIdOrderByCode(lot.getId());
        long totalQuantity = allLines.stream().mapToLong(ExpectedLine::getQuantityExpected).sum();
        long allocationPerUnit = totalQuantity > 0 ? lot.getAmountPaid().paise() / totalQuantity : 0;

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AddProductResponse(true, allLines.size(), totalQuantity, allocationPerUnit));
    }

    /**
     * The Intake screen's one read-only aggregate: header stats and lot-math-rail figures for a
     * single lot, from one call (design decision D5 of palletworks-intake). Composes {@code
     * LotClosing.crossCheckCost} (paid, pinned), a single pass over the lot's expected lines
     * (expected, counted, short, over), and a single pass over the lot's batches (MRP found,
     * projected retail) — no per-line or per-batch query.
     *
     * <p>Figures that would otherwise divide by an as-yet-zero denominator come back null rather
     * than as a computed zero or an exception: an empty lot, or a lot nothing has been counted
     * into yet, answers honestly (D5, D6).
     */
    @Transactional(readOnly = true)
    @GetMapping("/{lotId}/stats")
    ResponseEntity<LotIntakeStats> stats(@PathVariable UUID lotId) {
        if (lotRepository.findById(lotId).isEmpty()) {
            throw new IllegalArgumentException("no such lot: " + lotId);
        }

        List<ExpectedLine> lines = expectedLineRepository.findByLotIdOrderByCode(lotId);
        long expectedSum = 0;
        long countedSum = 0;
        long shortSum = 0;
        long overSum = 0;
        for (ExpectedLine line : lines) {
            long expected = line.getQuantityExpected();
            long counted = line.getQuantityCounted();
            expectedSum += expected;
            countedSum += counted;
            if (counted < expected) {
                shortSum += expected - counted;
            } else if (counted > expected) {
                overSum += counted - expected;
            }
        }
        Long expectedUnits = lines.isEmpty() ? null : expectedSum;

        long mrpFound = 0;
        long projectedRetail = 0;
        for (Batch batch : batchRepository.findByLotId(lotId)) {
            Money mrp = batch.getMrp();
            if (mrp != null) {
                mrpFound += mrp.paise() * batch.getQuantityReceived();
            }
            Money price = batch.sellingPrice();
            if (price != null) {
                projectedRetail += price.paise() * batch.sellableQuantity();
            }
        }

        LotCostReconciliation reconciliation = lotClosing.crossCheckCost(lotId);
        long paid = reconciliation.amountPaidPaise();
        long pinned = reconciliation.pinnedTotalPaise();

        Integer costOfMrpPercent =
                mrpFound == 0 ? null : (int) Math.round(paid * 100.0 / mrpFound);
        Long effectiveCostPerUnit = countedSum == 0 ? null : paid / countedSum;

        return ResponseEntity.ok(
                new LotIntakeStats(
                        lotId,
                        paid,
                        pinned,
                        mrpFound,
                        costOfMrpPercent,
                        expectedUnits,
                        countedSum,
                        shortSum,
                        overSum,
                        effectiveCostPerUnit,
                        projectedRetail));
    }

    /**
     * A delivery that cannot be allocated is the operator's to fix — an unknown product, a
     * pinned total that overshoots. The message says which, because "could not receive" leaves
     * someone holding a pallet with nothing to act on.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}

record LotSummaryDto(
    UUID id,
    String supplier,
    LocalDate receivedOn,
    boolean receivingComplete,
    boolean isManual,
    String categoryCode,
    long expected,
    long received,
    long unpacked,
    long rejected,
    long notReceived) {}

record AddProductResponse(
    boolean success,
    long totalProducts,
    long totalQuantity,
    long allocationPerUnit) {}
