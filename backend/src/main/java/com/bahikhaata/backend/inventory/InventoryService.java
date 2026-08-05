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

import com.bahikhaata.backend.catalog.Barcode;
import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.backend.pricing.Margins;
import com.bahikhaata.backend.shelf.PriceHistory;
import com.bahikhaata.backend.shelf.PriceHistoryRepository;
import com.bahikhaata.contracts.InventoryBatchLine;
import com.bahikhaata.contracts.InventoryDetail;
import com.bahikhaata.contracts.InventoryMovement;
import com.bahikhaata.contracts.InventoryRow;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.MovementType;
import com.bahikhaata.contracts.PriceChange;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Inventory screen's two payloads: the aggregate table ({@link #rows}) and one
 * product's full story ({@link #detail}, design decision D3 of palletworks-inventory). Both are
 * read-only and write nothing; the one write this feature adds — a batch's bin — lives in {@link
 * #setBin}, the only new write besides the price journal (design decision D8).
 */
@Service
public class InventoryService {

    private final InventoryQueryRepository query;
    private final ProductRepository products;
    private final BatchRepository batches;
    private final StockLedgerRepository ledger;
    private final BarcodeRepository barcodes;
    private final PriceHistoryRepository priceHistory;

    InventoryService(
            InventoryQueryRepository query,
            ProductRepository products,
            BatchRepository batches,
            StockLedgerRepository ledger,
            BarcodeRepository barcodes,
            PriceHistoryRepository priceHistory) {
        this.query = query;
        this.products = products;
        this.batches = batches;
        this.ledger = ledger;
        this.barcodes = barcodes;
        this.priceHistory = priceHistory;
    }

    @Transactional(readOnly = true)
    public List<InventoryRow> rows() {
        return query.aggregateRows().stream().map(this::toRow).toList();
    }

    private InventoryRow toRow(InventoryQueryRepository.AggregateRow row) {
        String lotLabel =
                row.lotCount() == 1
                        ? formatLot(row.lotSupplier(), row.lotReceivedOn())
                        : row.lotCount() + " lots";
        Long costBasisPaise = costBasis(row.costValuePaise(), row.uncostedRows(), row.onHand());
        Integer marginPercent = marginPercent(costBasisPaise, row.pricePaise());
        long ageDays = ageDays(row.firstReceiptAt());

        return new InventoryRow(
                row.productId(),
                row.productName(),
                row.condition(),
                lotLabel,
                bins(row.bins()),
                row.onHand(),
                costBasisPaise,
                row.pricePaise(),
                marginPercent,
                ageDays);
    }

    @Transactional(readOnly = true)
    public Optional<InventoryDetail> detail(UUID productId) {
        return products.findById(productId).map(this::composeDetail);
    }

    private InventoryDetail composeDetail(Product product) {
        UUID productId = product.getId();

        // Ascending is how the ledger repository already reads (FIFO order); reversed once here
        // rather than adding a second, newest-first derived query beside it.
        List<StockLedgerEntry> movementsAsc =
                ledger.findByProductIdOrderByEffectiveAtAscCreatedAtAsc(productId);
        long receivedUnits = 0;
        long soldUnits = 0;
        List<InventoryMovement> movements = new ArrayList<>(movementsAsc.size());
        for (StockLedgerEntry entry : movementsAsc) {
            if (entry.getMovementType() == MovementType.PURCHASE_RECEIPT) {
                receivedUnits += entry.getQuantity();
            } else if (entry.getMovementType() == MovementType.SALE) {
                soldUnits += -entry.getQuantity();
            }
        }
        for (int i = movementsAsc.size() - 1; i >= 0; i--) {
            StockLedgerEntry entry = movementsAsc.get(i);
            movements.add(
                    new InventoryMovement(
                            entry.getMovementType().name(),
                            entry.getQuantity(),
                            entry.getCogs() == null ? null : entry.getCogs().paise(),
                            entry.getEffectiveAt().toString()));
        }

        List<InventoryBatchLine> batchLines =
                batches.findByProductIdNewestFirst(productId).stream()
                        .map(this::toBatchLine)
                        .toList();

        List<PriceChange> priceChanges =
                priceHistory.findByProductIdOrderByCreatedAtDesc(productId).stream()
                        .map(
                                h ->
                                        new PriceChange(
                                                h.getOldPrice() == null ? null : h.getOldPrice().paise(),
                                                h.getNewPrice().paise(),
                                                h.getOperatorName(),
                                                h.getCreatedAt().toString()))
                        .toList();

        List<String> barcodeCodes =
                barcodes.findByProductId(productId).stream().map(Barcode::getCode).toList();

        // Headline cost/price/margin are the product's ordinary (GOOD-condition) figures — the
        // KPI strip is one number per figure, and GOOD is what "the product's price" means
        // elsewhere in the app (Product.sellingPrice); a damaged unit's own economics are still
        // visible per-batch below.
        InventoryQueryRepository.CostSummary goodCost = query.costSummary(productId, "GOOD");
        Long costBasisPaise = costBasis(goodCost.valuePaise(), goodCost.uncostedRows(), goodCost.quantity());
        Long sellingPricePaise =
                product.getSellingPrice() == null ? null : product.getSellingPrice().paise();
        Integer marginPercent = marginPercent(costBasisPaise, sellingPricePaise);

        return new InventoryDetail(
                productId,
                product.getName(),
                product.getCategory().code(),
                barcodeCodes,
                costBasisPaise,
                sellingPricePaise,
                marginPercent,
                receivedUnits,
                soldUnits,
                batchLines,
                movements,
                priceChanges);
    }

    private InventoryBatchLine toBatchLine(Batch batch) {
        Lot lot = batch.getLot();
        return new InventoryBatchLine(
                batch.getId(),
                batch.getCondition().name(),
                formatLot(lot.getSupplier(), lot.getReceivedOn() == null ? null : lot.getReceivedOn().toString()),
                batch.getBin(),
                batch.getQuantityReceived(),
                batch.getQuantityDamaged(),
                batch.isCosted() ? batch.getAllocatedUnitCost().paise() : null,
                batch.getMrp() == null ? null : batch.getMrp().paise(),
                batch.getCreatedAt() == null ? null : batch.getCreatedAt().toString());
    }

    /**
     * Sets a batch's bin, trimming blank to null (design decision D8). Empty when no batch has
     * this id — the controller turns that into a 404.
     *
     * <p>Returns the {@link Batch}, not its (possibly null) bin directly: {@code
     * Optional.map} with a mapper that can itself return null collapses "found, bin cleared to
     * null" into an empty {@code Optional} — indistinguishable from "no such batch". Returning the
     * batch keeps "found" and "what its bin now is" separate, so a caller clearing an existing bin
     * is never mistaken for a 404.
     */
    @Transactional
    public Optional<Batch> setBin(UUID batchId, String rawBin) {
        return batches.findById(batchId).map(
                batch -> {
                    batch.setBin(rawBin);
                    return batches.save(batch);
                });
    }

    /** Cost per unit, or null when any contributing batch is not yet costed (see class javadoc). */
    private static Long costBasis(long valuePaise, long uncostedRows, long onHand) {
        return uncostedRows > 0 || onHand <= 0 ? null : valuePaise / onHand;
    }

    private static Integer marginPercent(Long costBasisPaise, Long pricePaise) {
        if (costBasisPaise == null || pricePaise == null || pricePaise <= 0) {
            return null;
        }
        return Margins.grossMarginPercent(Money.ofPaise(pricePaise), Money.ofPaise(costBasisPaise))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** Whole UTC days since first receipt (design decision D4), or 0 if the product never received. */
    private static long ageDays(String firstReceiptAtIso) {
        if (firstReceiptAtIso == null) {
            return 0;
        }
        return Duration.between(Instant.parse(firstReceiptAtIso), Instant.now()).toDays();
    }

    private static List<String> bins(String groupConcatBins) {
        if (groupConcatBins == null || groupConcatBins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(groupConcatBins.split(",")).filter(s -> !s.isBlank()).toList();
    }

    private static String formatLot(String supplier, String receivedOn) {
        return supplier + " · " + (receivedOn == null ? "—" : receivedOn);
    }
}
