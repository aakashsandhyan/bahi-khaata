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

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.AddProductRequest;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CreateManualLotRequest;
import com.bahikhaata.contracts.LotLineResponse;
import com.bahikhaata.contracts.LotResponse;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.ReceiveLotRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    LotController(
            GoodsInService goodsIn,
            LotRepository lotRepository,
            BoxReceiptRepository boxReceiptRepository,
            ExpectedLineRepository expectedLineRepository,
            ProductRepository productRepository,
            BoxRepository boxRepository) {
        this.goodsIn = goodsIn;
        this.lotRepository = lotRepository;
        this.boxReceiptRepository = boxReceiptRepository;
        this.expectedLineRepository = expectedLineRepository;
        this.productRepository = productRepository;
        this.boxRepository = boxRepository;
    }

    @GetMapping
    ResponseEntity<List<LotSummaryDto>> listLots() {
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
                    lot.isManual(), expected, received, unpacked, rejected, notReceived);
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
        Lot lot = new Lot(
                request.supplier(),
                receivedOn,
                com.bahikhaata.contracts.Money.ofPaise(request.amountPaidPaise()),
                com.bahikhaata.contracts.Money.ZERO,
                request.allocationMethod(),
                true);
        lot = lotRepository.save(lot);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LotSummaryDto(lot.getId(), lot.getSupplier(), lot.getReceivedOn(),
                        lot.isReceivingComplete(), lot.isManual(), 0, 0, 0, 0, 0));
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
