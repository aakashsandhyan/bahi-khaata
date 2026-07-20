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
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.contracts.Money;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suggests a selling price for a product nobody has priced yet.
 *
 * <p>A suggestion is an offer, never an act. Calling this changes nothing: the product stays
 * unpriced and unsellable until a person sets a price, which is a separate, deliberate call.
 * That separation is the whole point — the system computes, a human decides.
 *
 * <p>Offered only for unpriced products. Once a product has a price, receiving stock at any
 * cost leaves it alone: repricing on arrival would invalidate every printed sticker and bin
 * card the moment it fired, and the customer reads the sticker.
 */
@Service
public class PriceSuggestions {

    private final ProductRepository products;
    private final BatchRepository batches;
    private final TargetMargins targetMargins;

    PriceSuggestions(
            ProductRepository products, BatchRepository batches, TargetMargins targetMargins) {
        this.products = products;
        this.batches = batches;
        this.targetMargins = targetMargins;
    }

    /**
     * A suggested price for an unpriced product, or empty where none can or should be offered.
     *
     * <p>Empty means one of three things, all legitimate: the product already has a price, no
     * stock has been received so there is no cost to work from, or the product does not exist.
     *
     * @param customMarginPercent a margin for this suggestion only, or null to resolve one
     */
    @Transactional(readOnly = true)
    public Optional<PriceSuggestion> suggestFor(UUID productId, Integer customMarginPercent) {
        Optional<Product> found = products.findById(productId);
        if (found.isEmpty() || found.get().isPriced()) {
            return Optional.empty();
        }
        Product product = found.get();

        // Costed from the most recent delivery, not the oldest: it is the best evidence of
        // what the product costs to buy now, which is what a price should be set against.
        List<Batch> received = batches.findByProductIdNewestFirst(productId);
        if (received.isEmpty()) {
            return Optional.empty();
        }
        Batch latest = received.get(0);

        int margin = targetMargins.resolve(product.getCategory(), customMarginPercent);
        Money unitCost = latest.getAllocatedUnitCost();

        return Optional.of(
                new PriceSuggestion(
                        productId,
                        Margins.priceForTargetMargin(unitCost, margin),
                        unitCost,
                        margin,
                        latest.getMrp(),
                        latest.isMrpEstimate()));
    }

    /**
     * A price the system would suggest, with what it was worked out from.
     *
     * <p>The workings travel with the number: a manager deciding whether to accept ₹26.79
     * needs to see that it came from a ₹18.75 cost at a 30% target, against an MRP of ₹40.
     * A bare figure invites either blind acceptance or distrust.
     *
     * @param productId the product
     * @param suggestedPrice what the system would charge
     * @param unitCost the cost it was derived from, from the most recent delivery
     * @param targetMarginPercent the margin used, after resolution
     * @param mrp the printed retail price of that delivery, for comparison
     * @param mrpIsEstimate whether that MRP was estimated rather than printed
     */
    public record PriceSuggestion(
            UUID productId,
            Money suggestedPrice,
            Money unitCost,
            int targetMarginPercent,
            Money mrp,
            boolean mrpIsEstimate) {}
}
