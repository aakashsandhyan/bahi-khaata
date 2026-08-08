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
import com.bahikhaata.contracts.LotLineResponse;
import com.bahikhaata.contracts.LotResponse;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.ReceiveLotRequest;
import com.bahikhaata.contracts.UpdateLotRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
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
    private final SupplierService supplierService;
    private final LotCategoryResolver lotCategories;
    private final CategoryCatalog categoryCatalog;
    private final LotEditPolicy lotEditPolicy;

    LotController(
            GoodsInService goodsIn,
            LotRepository lotRepository,
            BoxReceiptRepository boxReceiptRepository,
            ExpectedLineRepository expectedLineRepository,
            ProductRepository productRepository,
            BoxRepository boxRepository,
            SupplierService supplierService,
            LotCategoryResolver lotCategories,
            CategoryCatalog categoryCatalog,
            LotEditPolicy lotEditPolicy) {
        this.goodsIn = goodsIn;
        this.lotRepository = lotRepository;
        this.boxReceiptRepository = boxReceiptRepository;
        this.expectedLineRepository = expectedLineRepository;
        this.productRepository = productRepository;
        this.boxRepository = boxRepository;
        this.supplierService = supplierService;
        this.lotCategories = lotCategories;
        this.categoryCatalog = categoryCatalog;
        this.lotEditPolicy = lotEditPolicy;
    }

    /** The category a lot's own field, then anything a manual lot's list falls back to, must be one of. */
    private void requireKnownCategory(String code) {
        if (!categoryCatalog.allCodes().contains(code)) {
            throw new IllegalArgumentException("unknown category: " + code);
        }
    }

    @GetMapping
    ResponseEntity<List<LotSummaryDto>> listLots() {
        Map<UUID, String> categoryByLot = lotCategories.categoryByLot();
        List<LotSummaryDto> results = lotRepository.findAll().stream()
            .filter(Lot::isOpen)
            .map(lot -> toSummary(lot, categoryByLot))
            .sorted(Comparator
                .comparing((LotSummaryDto l) -> l.receivingComplete())
                .thenComparing(Comparator.comparing((LotSummaryDto l) -> l.id()).reversed()))
            .toList();
        return ResponseEntity.ok(results);
    }

    /**
     * A lot's box counts plus its category — stored on the lot first, the derived resolver value
     * as fallback for a lot that predates or has not set one. Shared by the list, create, and
     * update responses so the three never drift on how a lot is summarised, and so the edit
     * modal always has the lot's real supplier/amount/freight/allocation method to pre-fill from
     * rather than opening blind.
     */
    private LotSummaryDto toSummary(Lot lot, Map<UUID, String> categoryByLot) {
        List<BoxReceipt> boxes = boxReceiptRepository.findByLotId(lot.getId());
        long expected = boxes.size();
        long received = boxReceiptRepository.countByLotIdAndState(lot.getId(), com.bahikhaata.contracts.BoxState.RECEIVED);
        long unpacked = boxReceiptRepository.countByLotIdAndState(lot.getId(), com.bahikhaata.contracts.BoxState.UNPACKED);
        long rejected = boxReceiptRepository.countByLotIdAndState(lot.getId(), com.bahikhaata.contracts.BoxState.REJECTED);
        long notReceived = boxReceiptRepository.countByLotIdAndState(lot.getId(), com.bahikhaata.contracts.BoxState.NOT_RECEIVED);
        String category = lot.getCategory() != null ? lot.getCategory() : categoryByLot.get(lot.getId());
        String supplierId = lot.getSupplierRef() != null ? lot.getSupplierRef().getId().toString() : null;
        return new LotSummaryDto(lot.getId(), lot.getSupplier(), supplierId, lot.getReceivedOn(),
                lot.isReceivingComplete(), lot.isManual(), category, lot.getAmountPaid().paise(),
                lot.getFreight().paise(), lot.getAllocationMethod(), expected, received, unpacked, rejected,
                notReceived);
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
        if (request.categoryCode() != null && !request.categoryCode().isBlank()) {
            requireKnownCategory(request.categoryCode());
            lot.setCategory(request.categoryCode());
        }
        lot = lotRepository.save(lot);

        // A freshly created manual lot has no boxes yet, so toSummary's box counts all come back
        // zero — the same zeros this endpoint always returned, just computed the shared way.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toSummary(lot, lotCategories.categoryByLot()));
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

        // A line's own category wins; otherwise it falls back to the lot's default, so a mixed
        // lot only needs an override on the products that differ from the rest.
        String effectiveCategory = request.categoryCode() != null && !request.categoryCode().isBlank()
                ? request.categoryCode()
                : lot.getCategory();
        if (effectiveCategory == null) {
            throw new IllegalArgumentException(
                    "categoryCode required: this lot has no default category to fall back on");
        }
        requireKnownCategory(effectiveCategory);

        // Create new product for manual entry
        Product product = new Product(
                request.name(),
                Category.of(effectiveCategory),
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
     * Corrects a manual-entry mistake on a lot: supplier, date, amount paid, freight,
     * allocation method, or default category. Guarded by {@link LotEditPolicy} — once stock
     * from the lot has been consumed, its costs are already recorded against sales, so none of
     * these fields may move without rewriting margin history.
     *
     * <p>Every field is optional: {@code null} leaves it unchanged. {@code categoryCode} is the
     * one field where an explicit {@code ""} means something different from absent — it clears
     * the lot back to "no default" rather than leaving the existing one in place.
     */
    @PutMapping("/{lotId}")
    ResponseEntity<LotSummaryDto> updateLot(
            @PathVariable UUID lotId, @RequestBody UpdateLotRequest request) {
        Lot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new IllegalArgumentException("lot not found"));

        lotEditPolicy.requireEditable(lotId);

        if (request.supplierId() != null) {
            lot.setSupplierRef(supplierService.resolveActiveSupplier(request.supplierId()));
        }
        if (request.receivedOn() != null) {
            lot.setReceivedOn(LocalDate.parse(request.receivedOn()));
        }
        if (request.amountPaidPaise() != null) {
            lot.setAmountPaid(Money.ofPaise(request.amountPaidPaise()));
        }
        if (request.freightPaise() != null) {
            lot.setFreight(Money.ofPaise(request.freightPaise()));
        }
        if (request.allocationMethod() != null) {
            lot.setAllocationMethod(request.allocationMethod());
        }
        if (request.categoryCode() != null) {
            if (request.categoryCode().isBlank()) {
                lot.setCategory(null);
            } else {
                requireKnownCategory(request.categoryCode());
                lot.setCategory(request.categoryCode());
            }
        }

        lot = lotRepository.save(lot);

        return ResponseEntity.ok(toSummary(lot, lotCategories.categoryByLot()));
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

    /**
     * An edit to a lot whose stock has already been consumed — its costs are load-bearing on
     * recorded sales, so the fix must be a recorded adjustment, not a silent rewrite. 409
     * matches the house convention for "exists, but not editable in its current state".
     */
    @ExceptionHandler(LotFrozenException.class)
    ResponseEntity<String> frozen(LotFrozenException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
}

record LotSummaryDto(
    UUID id,
    String supplier,
    String supplierId,
    LocalDate receivedOn,
    boolean receivingComplete,
    boolean isManual,
    String categoryCode,
    long amountPaidPaise,
    long freightPaise,
    AllocationMethod allocationMethod,
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
