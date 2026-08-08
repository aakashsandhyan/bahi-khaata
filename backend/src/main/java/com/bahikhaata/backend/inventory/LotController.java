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
import com.bahikhaata.contracts.CostAnchor;
import com.bahikhaata.contracts.CostBasisStrategy;
import com.bahikhaata.contracts.CreateManualLotRequest;
import com.bahikhaata.contracts.LotCostReconciliation;
import com.bahikhaata.contracts.LotLineResponse;
import com.bahikhaata.contracts.LotResponse;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.MrpRateBand;
import com.bahikhaata.contracts.MultiplierBase;
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
    private final LotCostBasis lotCostBasis;
    private final LotMrpRateBandRepository rateBandRepository;
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
            CategoryCatalog categoryCatalog,
            LotEditPolicy lotEditPolicy,
            LotCostBasis lotCostBasis,
            LotMrpRateBandRepository rateBandRepository,
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
        this.categoryCatalog = categoryCatalog;
        this.lotEditPolicy = lotEditPolicy;
        this.lotCostBasis = lotCostBasis;
        this.rateBandRepository = rateBandRepository;
        this.batchRepository = batchRepository;
        this.lotClosing = lotClosing;
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
     *
     * <p>Where a lot declares a cost basis, the summary also carries it (for the edit modal to
     * pre-fill) and the amount-paid cross-check ({@link LotClosing#crossCheckCost}) — a reporting
     * figure, never withheld, but only meaningful once there is a basis whose derived costs the
     * amount paid is being checked against; a lot with no declared basis leaves both null.
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

        List<MrpRateBand> bands = List.of();
        Long variance = null;
        Boolean reconciles = null;
        if (lot.declaresCostBasis()) {
            bands = rateBandRepository.findByLotIdOrderByMinMrp(lot.getId()).stream()
                    .map(band -> new MrpRateBand(
                            band.getMinMrp().paise(),
                            band.getMaxMrp() == null ? null : band.getMaxMrp().paise(),
                            band.getCost().paise()))
                    .toList();
            LotCostReconciliation crossCheck = lotClosing.crossCheckCost(lot.getId());
            variance = crossCheck.differencePaise();
            reconciles = crossCheck.reconciles();
        }

        return new LotSummaryDto(lot.getId(), lot.getSupplier(), supplierId, lot.getReceivedOn(),
                lot.isReceivingComplete(), lot.isManual(), category, lot.getAmountPaid().paise(),
                lot.getFreight().paise(), lot.getAllocationMethod(), expected, received, unpacked, rejected,
                notReceived, lot.getCostBasisStrategy(), lot.getCostAnchor(),
                lot.getFlatUnitCost() == null ? null : lot.getFlatUnitCost().paise(), lot.getPercentBp(),
                lot.getMultiplierMilli(), lot.getMultiplierBase(), bands, variance, reconciles);
    }

    /**
     * Validates a candidate cost basis, then sets it onto the lot (scalars only — the caller
     * saves the lot and then persists the bands with {@link #saveRateBands}, in that order, so an
     * invalid request is rejected before anything is written and the bands' foreign key always
     * has a saved lot row to point at). Shared by create and update: both offer the same seven
     * fields, and a strategy is validated the same way regardless of which endpoint declared it.
     */
    private void applyCostBasis(
            Lot lot,
            CostBasisStrategy strategy,
            CostAnchor anchor,
            Long flatUnitCostPaise,
            Long percentBp,
            Long multiplierMilli,
            MultiplierBase multiplierBase,
            List<MrpRateBand> bands) {
        Money flatUnitCost = flatUnitCostPaise == null ? null : Money.ofPaise(flatUnitCostPaise);
        List<MrpRateBand> rateBands = bands == null ? List.of() : bands;
        lotCostBasis.requireValidBasis(
                strategy, anchor, flatUnitCost, percentBp, multiplierMilli, multiplierBase, rateBands);

        lot.setCostBasisStrategy(strategy);
        lot.setCostAnchor(anchor);
        lot.setFlatUnitCost(flatUnitCost);
        lot.setPercentBp(percentBp);
        lot.setMultiplierMilli(multiplierMilli);
        lot.setMultiplierBase(multiplierBase);
    }

    /** Replaces a lot's rate-card rows whole — an edit does not merge into the existing bands. */
    private void saveRateBands(Lot lot, List<MrpRateBand> bands) {
        rateBandRepository.deleteByLotId(lot.getId());
        for (MrpRateBand band : bands) {
            rateBandRepository.save(
                    new LotMrpRateBand(
                            lot,
                            Money.ofPaise(band.minMrpPaise()),
                            band.maxMrpPaise() == null ? null : Money.ofPaise(band.maxMrpPaise()),
                            Money.ofPaise(band.costPaise())));
        }
    }

    /**
     * Re-derives and re-pins every batch of a lot whose cost basis just changed, so the new basis
     * takes effect on stock already counted but not yet sold. Only reachable once the lot has
     * passed {@link LotEditPolicy#requireEditable}, which for the whole-lot freeze rule means no
     * stock from any batch here has been consumed — so there is nothing "already sold" to protect
     * and every batch is fair game. A batch the new basis cannot yet resolve — its anchor still
     * unknown — is left as it was rather than un-pinned; {@link Batch} has no "uncost" operation,
     * and a stale pin under the old basis is judged the lesser problem versus reverting cost to
     * something no code path can then re-derive on its own.
     */
    private void rePinBatches(Lot lot) {
        List<LotMrpRateBand> bands = rateBandRepository.findByLotIdOrderByMinMrp(lot.getId());
        for (Batch batch : batchRepository.findByLotId(lot.getId())) {
            Money anchor = lotCostBasis.anchorValue(lot, batch, batch.getProduct());
            Money statedValue =
                    expectedLineRepository
                            .findByLotIdAndProductIdOrderByCode(lot.getId(), batch.getProduct().getId())
                            .stream()
                            .findFirst()
                            .map(ExpectedLine::getStatedValue)
                            .orElse(null);
            Money resolved = lotCostBasis.unitCost(lot, bands, anchor, statedValue);
            if (resolved != null) {
                batch.pinUnitCost(resolved);
            }
        }
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
        // Validated before anything is saved, so a bad cost basis never reaches the database.
        if (request.costBasisStrategy() != null) {
            applyCostBasis(
                    lot, request.costBasisStrategy(), request.costAnchor(), request.flatUnitCostPaise(),
                    request.percentBp(), request.multiplierMilli(), request.multiplierBase(),
                    request.rateBands());
        }
        lot = lotRepository.save(lot);
        if (request.costBasisStrategy() != null) {
            saveRateBands(lot, request.rateBands());
        }

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
     * allocation method, default category, or cost basis. Guarded by {@link LotEditPolicy} —
     * once stock from the lot has been consumed, its costs are already recorded against sales,
     * so none of these fields may move without rewriting margin history.
     *
     * <p>Every field is optional: {@code null} leaves it unchanged. {@code categoryCode} is one
     * exception — an explicit {@code ""} clears it back to "no default" rather than leaving the
     * existing one in place. {@code costBasisStrategy} is the other: naming one replaces the
     * lot's whole cost basis (see {@link #applyCostBasis}), and having done so, every
     * not-yet-consumed batch in the lot is re-derived and re-pinned to it — safe only because
     * passing the freeze guard below means nothing in this lot has been consumed yet.
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
        // Validated before anything is saved, so a bad cost basis never reaches the database.
        if (request.costBasisStrategy() != null) {
            applyCostBasis(
                    lot, request.costBasisStrategy(), request.costAnchor(), request.flatUnitCostPaise(),
                    request.percentBp(), request.multiplierMilli(), request.multiplierBase(),
                    request.rateBands());
        }

        lot = lotRepository.save(lot);

        if (request.costBasisStrategy() != null) {
            saveRateBands(lot, request.rateBands());
            rePinBatches(lot);
        }

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
    long notReceived,
    // Null throughout when the lot declares no cost basis — the whole group travels together,
    // same as on the request DTOs.
    CostBasisStrategy costBasisStrategy,
    CostAnchor costAnchor,
    Long flatUnitCostPaise,
    Long percentBp,
    Long multiplierMilli,
    MultiplierBase multiplierBase,
    List<MrpRateBand> rateBands,
    // The amount-paid cross-check (LotClosing.crossCheckCost): null unless a basis is declared.
    Long costVariancePaise,
    Boolean costReconciles) {}

record AddProductResponse(
    boolean success,
    long totalProducts,
    long totalQuantity,
    long allocationPerUnit) {}
