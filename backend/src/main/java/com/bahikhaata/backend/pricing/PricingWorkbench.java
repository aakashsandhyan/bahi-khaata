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

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.backend.shelf.ProductPricing;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.PriceProposal;
import com.bahikhaata.contracts.PriceableItem;
import com.bahikhaata.contracts.StockCondition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deciding what the goods sell for, once it is known what they cost.
 *
 * <p>An admin task, and the first screen that shows cost and margin — figures an operator must
 * never see. It is served under {@code /api/admin} for that reason, so a role gate later is a
 * matter of the path and not of hunting down every endpoint.
 *
 * <p>The shop earns on what it paid, and the price is judged against three figures at once: the
 * cost it must clear, the online price it must beat (the promise to be cheaper than online), and
 * the printed MRP it may never exceed (the law). A margin here is huge — the goods are bought at
 * a quarter to a third of what they sold for online — so the work is less about squeezing margin
 * than about setting a believable price that keeps the promise and stays legal.
 *
 * <p>Nothing is applied while previewing. A manager sets a margin, sees what it does to every
 * line, and only then commits — because a rule that looks right in the aggregate can put a
 * single odd line below cost or above its MRP, and that line is the whole point of looking.
 */
@Service
public class PricingWorkbench {

    private final BatchRepository batches;
    private final ProductPricing pricing;

    PricingWorkbench(BatchRepository batches, ProductPricing pricing) {
        this.batches = batches;
        this.pricing = pricing;
    }

    /**
     * Goods ready to be priced: their delivery closed, their cost known.
     *
     * <p>Stock from an open delivery is left out — its cost is not settled, and a margin against
     * an unknown cost is not a margin. One row per priced-separately condition, so damaged goods
     * appear beside the sound ones rather than hidden behind them.
     */
    @Transactional(readOnly = true)
    public List<PriceableItem> priceable(String categoryCode) {
        return batches.findAll().stream()
                .filter(Batch::isCosted)
                .filter(batch -> batch.getCondition() != StockCondition.UNUSABLE)
                .filter(batch -> categoryCode == null
                        || batch.getProduct().getCategory().code().equals(categoryCode))
                .map(this::itemFor)
                .toList();
    }

    /**
     * What a target margin would make of each product, without committing anything.
     *
     * <p>The margin is earned on cost: price = cost ÷ (1 − margin). Each proposal then carries
     * where that price sits against the online price and the MRP, so the lines a rule would push
     * below the promise or above the law stand out before a single one is set.
     */
    @Transactional(readOnly = true)
    public List<PriceProposal> preview(List<UUID> productIds, int marginPercent) {
        return productIds.stream()
                .map(id -> propose(id, marginPercent))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** As {@link #preview}, for a whole category at once. */
    @Transactional(readOnly = true)
    public List<PriceProposal> previewCategory(String categoryCode, int marginPercent) {
        return priceable(categoryCode).stream()
                .map(item -> propose(item.productId(), marginPercent))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Sets a price.
     *
     * <p>Through the same gate as any other pricing: the cost must be known and the price may not
     * exceed the MRP, both refused rather than warned. Beating the online price is the shop's
     * promise, not the law, so it is shown and not enforced — a manager may knowingly price at or
     * above it when the online figure is stale or wrong.
     */
    @Transactional
    public void apply(UUID productId, Money price) {
        pricing.setSellingPrice(productId, price);
    }

    /**
     * Prices every still-unpriced product in a category at a target margin.
     *
     * <p>Skips what is already priced, so running it again does not quietly overwrite a figure a
     * manager set by hand. Skips too any line the rule would push above its MRP — that one needs
     * a person, and silently pricing it at the cap would hide the problem rather than surface it.
     */
    @Transactional
    public BulkResult applyCategory(String categoryCode, int marginPercent) {
        int priced = 0;
        int skippedAlreadyPriced = 0;
        int skippedAboveMrp = 0;
        for (PriceableItem item : priceable(categoryCode)) {
            if (item.currentPricePaise() != null) {
                skippedAlreadyPriced++;
                continue;
            }
            PriceProposal proposal = propose(item.productId(), marginPercent);
            if (proposal == null) {
                continue;
            }
            if (proposal.aboveMrp()) {
                skippedAboveMrp++;
                continue;
            }
            pricing.setSellingPrice(item.productId(), Money.ofPaise(proposal.pricePaise()));
            priced++;
        }
        return new BulkResult(priced, skippedAlreadyPriced, skippedAboveMrp);
    }

    private PriceableItem itemFor(Batch batch) {
        Product product = batch.getProduct();
        Money online = product.getOnlinePrice();
        Money mrp = batch.getMrp();
        Money current = batch.sellingPrice();
        return new PriceableItem(
                product.getId(),
                product.getName(),
                product.getCategory().code(),
                batch.getAllocatedUnitCost().paise(),
                online == null ? null : online.paise(),
                mrp == null ? null : mrp.paise(),
                batch.isMrpEstimate(),
                current == null ? null : current.paise(),
                batch.getCondition().name());
    }

    private PriceProposal propose(UUID productId, int marginPercent) {
        Batch batch =
                batches.findAll().stream()
                        .filter(Batch::isCosted)
                        .filter(b -> b.getProduct().getId().equals(productId))
                        .findFirst()
                        .orElse(null);
        if (batch == null) {
            return null;
        }

        Money cost = batch.getAllocatedUnitCost();
        Money price = Margins.priceForTargetMargin(cost, marginPercent);
        Money online = batch.getProduct().getOnlinePrice();
        Money mrp = batch.getMrp();

        Integer percentOfMrp =
                mrp == null || mrp.paise() == 0
                        ? null
                        : BigDecimal.valueOf(price.paise() * 100L)
                                .divide(BigDecimal.valueOf(mrp.paise()), 0, RoundingMode.HALF_UP)
                                .intValue();
        Boolean beatsOnline = online == null ? null : price.paise() < online.paise();
        boolean aboveMrp = mrp != null && price.paise() > mrp.paise();

        return new PriceProposal(
                productId,
                price.paise(),
                Margins.grossMarginPercent(price, cost).setScale(0, RoundingMode.HALF_UP).intValue(),
                percentOfMrp,
                beatsOnline,
                aboveMrp);
    }

    /** What a bulk pricing did, and what it deliberately left for a person. */
    public record BulkResult(int priced, int skippedAlreadyPriced, int skippedAboveMrp) {}
}
