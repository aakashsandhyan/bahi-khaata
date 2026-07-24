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
import com.bahikhaata.contracts.BacklogItem;
import com.bahikhaata.contracts.IssueTypeOption;
import com.bahikhaata.contracts.ProductStates;
import com.bahikhaata.contracts.RemediationLine;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
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

    GoodsRemediation(
            GoodsInCounting counting,
            BatchRepository batches,
            StockLedgerRepository ledger,
            ProductRepository products,
            LotRepository lots,
            IssueTypeRepository issueTypes) {
        this.counting = counting;
        this.batches = batches;
        this.ledger = ledger;
        this.products = products;
        this.lots = lots;
        this.issueTypes = issueTypes;
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
