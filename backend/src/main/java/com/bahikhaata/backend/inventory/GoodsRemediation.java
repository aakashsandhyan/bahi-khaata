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

import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.BacklogItem;
import com.bahikhaata.contracts.ExtraRecord;
import com.bahikhaata.contracts.IssueTypeOption;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.ProductStates;
import com.bahikhaata.contracts.ProductSummary;
import com.bahikhaata.contracts.RemediationLine;
import com.bahikhaata.contracts.ReviewItem;
import com.bahikhaata.contracts.ShortLine;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moving stock between the states it can be in, as goods are prepared, rescued, or found wanting.
 *
 * <p>Goods rarely arrive perfect. Some are cleaned or rebuilt from another's parts into sellable
 * stock; some are found damaged only after counting; some turn out scrap. Each is one unit leaving
 * one state and joining another. This records that, reusing the counting service's batch-and-ledger
 * path so the append-only rules and the MRP inheritance are written in one place.
 *
 * <p>Only the stock-bearing states — GOOD and DAMAGED — reach the ledger. UNUSABLE and NEEDS_WORK
 * sit off it, so on-hand rises when scrap or needs-work is made ready and falls when sellable stock
 * is scrapped, while a move between two stock-bearing states leaves on-hand unchanged. Nothing is
 * edited: the source loses units and, if it was on the ledger, a correcting entry is appended.
 */
@Service
public class GoodsRemediation {

    private final GoodsInCounting counting;
    private final BatchRepository batches;
    private final StockLedgerRepository ledger;
    private final ProductRepository products;
    private final LotRepository lots;
    private final IssueTypeRepository issueTypes;
    private final BarcodeRepository barcodes;
    private final UnlistedFindRepository unlistedFinds;
    private final ExpectedLineRepository expectedLines;

    GoodsRemediation(
            GoodsInCounting counting,
            BatchRepository batches,
            StockLedgerRepository ledger,
            ProductRepository products,
            LotRepository lots,
            IssueTypeRepository issueTypes,
            BarcodeRepository barcodes,
            UnlistedFindRepository unlistedFinds,
            ExpectedLineRepository expectedLines) {
        this.counting = counting;
        this.batches = batches;
        this.ledger = ledger;
        this.products = products;
        this.lots = lots;
        this.issueTypes = issueTypes;
        this.barcodes = barcodes;
        this.unlistedFinds = unlistedFinds;
        this.expectedLines = expectedLines;
    }

    /** Extras recorded against boxes, still held as their own product, waiting to be linked. */
    @Transactional(readOnly = true)
    public List<ExtraRecord> extras() {
        return unlistedFinds.findAll().stream()
                .filter(f -> f.getQuantity() > 0)
                .map(
                        f ->
                                new ExtraRecord(
                                        f.getProduct().getId(),
                                        f.getProduct().getName(),
                                        f.getProduct().getCategory().code(),
                                        f.getLot().getId(),
                                        f.getBox().getTrackingNumber(),
                                        barcodes.findByProductId(f.getProduct().getId()).stream()
                                                .map(b -> b.getCode())
                                                .findFirst()
                                                .orElse(null),
                                        f.getQuantity()))
                .toList();
    }

    /** The lines of a delivery still missing units — what an extra might really be. */
    @Transactional(readOnly = true)
    public List<ShortLine> shortsInLot(UUID lotId) {
        return expectedLines.findByLotIdOrderByCode(lotId).stream()
                .filter(l -> l.getQuantityOutstanding() > 0)
                .map(
                        l ->
                                new ShortLine(
                                        l.getId(),
                                        l.getProduct().getId(),
                                        l.getProduct().getName(),
                                        l.getCode(),
                                        l.getBox().getTrackingNumber(),
                                        l.getQuantityExpected(),
                                        l.getQuantityCounted(),
                                        l.getQuantityOutstanding()))
                .toList();
    }

    /**
     * Reattributes a quantity of an extra to the real product it turned out to be, filling that
     * product's shortfall. The units never move — only which product owns them: the target gains a
     * receipt and the count against its short line, the extra loses an equal adjustment, so on-hand
     * is unchanged and the ledger is only appended to. Open-lot only.
     */
    @Transactional
    public void linkExtra(UUID extraProductId, UUID targetLineId, long quantity, Instant at) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("must link a positive number, was " + quantity);
        }
        ExpectedLine target =
                expectedLines
                        .findById(targetLineId)
                        .orElseThrow(() -> new IllegalArgumentException("no such line: " + targetLineId));
        Lot lot = target.getLot();
        if (!lot.isOpen()) {
            throw new IllegalStateException(
                    "lot " + lot.getId() + " is closed; its counts are settled and an extra can no"
                            + " longer be linked into it");
        }
        Batch extraBatch =
                batches.findByLotIdAndProductIdAndCondition(
                                lot.getId(), extraProductId, StockCondition.GOOD)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "that extra has no sound stock in this delivery to link"));
        if (extraBatch.getQuantityReceived() < quantity) {
            throw new IllegalArgumentException(
                    "cannot link " + quantity + " when only " + extraBatch.getQuantityReceived()
                            + " extra are held");
        }
        Product extra = extraBatch.getProduct();

        // Target side: count against the short line — fills the shortfall, adds to the target's
        // batch, and writes the receipt. The extra's MRP carries over, since it was read off the
        // same physical pack.
        Money mrp = extraBatch.getMrp();
        counting.countExpected(
                targetLineId, StockCondition.GOOD, quantity, mrp, extraBatch.isMrpEstimate(),
                null, at);

        // Extra side: the phantom loses the units it never really held.
        extraBatch.removeCounted(quantity);
        ledger.save(StockLedgerEntry.adjustment(extra, extraBatch, -quantity, at));

        // Take the reattributed units off the extra's find record so it stops listing them. The
        // record cannot hold zero (a CHECK enforces it), so a fully-linked extra is deleted.
        unlistedFinds.findByLotId(lot.getId()).stream()
                .filter(f -> f.getProduct().getId().equals(extraProductId) && f.getQuantity() > 0)
                .findFirst()
                .ifPresent(
                        f -> {
                            long take = Math.min(quantity, f.getQuantity());
                            if (take >= f.getQuantity()) {
                                unlistedFinds.delete(f);
                            } else {
                                f.reduce(take);
                            }
                        });
    }

    /** The states a scanned code's product is held in, so a rescue can start from a scan. */
    @Transactional(readOnly = true)
    public ProductStates statesByCode(String code) {
        return barcodes
                .findByCode(code)
                .map(b -> statesOf(b.getProduct().getId()))
                .orElseThrow(
                        () -> new IllegalArgumentException("no goods scan as \"" + code + "\""));
    }

    /** Products whose name contains the query, for looking one up to change its stock's state. */
    @Transactional(readOnly = true)
    public List<ProductSummary> search(String query) {
        return products.findTop25ByNameContainingIgnoreCaseOrderByName(query).stream()
                .map(p -> new ProductSummary(p.getId(), p.getName(), p.getCategory().code()))
                .toList();
    }

    /** The kinds of work offered for a department — the menu when marking an item needs-work. */
    @Transactional(readOnly = true)
    public List<IssueTypeOption> issueTypesFor(String categoryCode) {
        return issueTypes.findForCategory(categoryCode).stream()
                .map(v -> new IssueTypeOption(v.getCode(), v.getLabel()))
                .toList();
    }

    /**
     * A product and every state its stock is held in, so a rescue can move units between them. Zero
     * piles are dropped — there is nothing to move from an empty one.
     */
    @Transactional(readOnly = true)
    public ProductStates statesOf(UUID productId) {
        Product product =
                products.findById(productId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("no such product: " + productId));
        Map<String, String> labels = issueLabels();
        List<RemediationLine> lines =
                batches.findByProductId(productId).stream()
                        .filter(b -> b.getQuantityReceived() > 0)
                        .map(b -> line(b, labels))
                        .toList();
        return new ProductStates(
                productId, product.getName(), product.getCategory().code(), lines);
    }

    /**
     * Damaged and broken goods, with the note taken when counted, for sorting the fixable ones into
     * needs-work from the true seconds — the pile that predates the needs-work state. Those bearing
     * a note come first, since a note is what makes a pile worth a second look.
     */
    @Transactional(readOnly = true)
    public List<ReviewItem> reviewList() {
        List<Batch> pile = new ArrayList<>();
        pile.addAll(batches.findByConditionAndQuantityReceivedGreaterThan(StockCondition.DAMAGED, 0));
        pile.addAll(batches.findByConditionAndQuantityReceivedGreaterThan(StockCondition.UNUSABLE, 0));
        return pile.stream()
                .map(
                        b ->
                                new ReviewItem(
                                        b.getProduct().getId(),
                                        b.getProduct().getName(),
                                        b.getProduct().getCategory().code(),
                                        b.getLot().getId(),
                                        b.getCondition(),
                                        b.getRemark(),
                                        b.getQuantityReceived()))
                .sorted(
                        Comparator.comparing((ReviewItem r) -> r.remark() == null)
                                .thenComparing(ReviewItem::productName))
                .toList();
    }

    /** The needs-work backlog: every pile of goods waiting on preparation, for routing the work. */
    @Transactional(readOnly = true)
    public List<BacklogItem> backlog() {
        Map<String, String> labels = issueLabels();
        return batches
                .findByConditionAndQuantityReceivedGreaterThan(StockCondition.NEEDS_WORK, 0)
                .stream()
                .map(
                        b ->
                                new BacklogItem(
                                        b.getProduct().getId(),
                                        b.getProduct().getName(),
                                        b.getProduct().getCategory().code(),
                                        b.getLot().getId(),
                                        b.getIssueType(),
                                        labels.getOrDefault(b.getIssueType(), b.getIssueType()),
                                        b.getQuantityReceived()))
                .toList();
    }

    private RemediationLine line(Batch b, Map<String, String> labels) {
        return new RemediationLine(
                b.getLot().getId(),
                b.getCondition(),
                b.getIssueType(),
                b.getIssueType() == null ? null : labels.getOrDefault(b.getIssueType(), b.getIssueType()),
                b.getQuantityReceived());
    }

    private Map<String, String> issueLabels() {
        return issueTypes.findAll().stream()
                .collect(Collectors.toMap(IssueType::getCode, IssueType::getLabel));
    }

    /**
     * Moves a quantity of a product's units from one state to another within an open lot.
     *
     * <p>The issue type names the work for a needs-work side and must be null for any other, the
     * same rule the batch enforces. Refused if the lot is closed — its costs are settled — or if
     * the source holds fewer units than asked.
     */
    @Transactional
    public void changeState(
            UUID productId,
            UUID lotId,
            StockCondition from,
            String fromIssueType,
            StockCondition to,
            String toIssueType,
            long quantity,
            Instant at) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("must move a positive number, was " + quantity);
        }
        requireIssueType(from, fromIssueType);
        requireIssueType(to, toIssueType);
        if (from == to && Objects.equals(fromIssueType, toIssueType)) {
            throw new IllegalArgumentException("the source and target state are the same");
        }

        Lot lot =
                lots.findById(lotId)
                        .orElseThrow(() -> new IllegalArgumentException("no such lot: " + lotId));
        if (!lot.isOpen()) {
            throw new IllegalStateException(
                    "lot " + lotId + " is closed; its costs are settled and its goods can no longer"
                            + " be moved between states");
        }
        Product product =
                products.findById(productId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("no such product: " + productId));

        Batch source =
                sourceBatch(lotId, productId, from, fromIssueType)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "no " + from + " stock of this product in this delivery to"
                                                + " move"));
        if (source.getQuantityReceived() < quantity) {
            throw new IllegalArgumentException(
                    "cannot move " + quantity + " when only " + source.getQuantityReceived()
                            + " are held");
        }

        source.removeCounted(quantity);
        // A stock-bearing source loses stock on the ledger; an off-ledger one never had it there.
        if (isStockBearing(from)) {
            ledger.save(StockLedgerEntry.adjustment(product, source, -quantity, at));
        }
        // The target side goes through the counting path, which inherits the product's MRP and
        // writes a receipt only when the target is stock-bearing.
        counting.addToBatch(lot, product, to, quantity, null, false, null, toIssueType, at);
    }

    private Optional<Batch> sourceBatch(
            UUID lotId, UUID productId, StockCondition from, String fromIssueType) {
        return from == StockCondition.NEEDS_WORK
                ? batches.findByLotIdAndProductIdAndConditionAndIssueType(
                        lotId, productId, from, fromIssueType)
                : batches.findByLotIdAndProductIdAndCondition(lotId, productId, from);
    }

    private static boolean isStockBearing(StockCondition condition) {
        return condition == StockCondition.GOOD || condition == StockCondition.DAMAGED;
    }

    private static void requireIssueType(StockCondition condition, String issueType) {
        if ((condition == StockCondition.NEEDS_WORK) != (issueType != null)) {
            throw new IllegalArgumentException(
                    "a needs-work side names the work it needs; any other names none");
        }
    }
}
