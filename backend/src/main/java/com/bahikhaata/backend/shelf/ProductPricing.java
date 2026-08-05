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
package com.bahikhaata.backend.shelf;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.contracts.Money;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Setting what a product sells for, once that can honestly be decided.
 *
 * <p>Two conditions, both refusals rather than warnings.
 *
 * <p>The cost has to be known, which means the delivery it came from has been closed. A margin
 * against an unknown cost is not a margin, and a price set on one would look considered while
 * resting on nothing.
 *
 * <p>The price cannot exceed the MRP printed on the goods. That is not a policy this shop
 * chose: selling above the printed maximum retail price is unlawful in India, and the label
 * shows the customer a saving measured against it.
 *
 * <p>Every price this sets is journaled to {@link PriceHistory} before returning — one hook, not
 * five, per design decision D5 of palletworks-inventory. This is the only class that ever saves a
 * {@link PriceHistory} row.
 */
@Service
public class ProductPricing {

    private final ProductRepository products;
    private final BatchRepository batches;
    private final PriceHistoryRepository priceHistory;

    ProductPricing(
            ProductRepository products, BatchRepository batches, PriceHistoryRepository priceHistory) {
        this.products = products;
        this.batches = batches;
        this.priceHistory = priceHistory;
    }

    @Transactional
    public Product setSellingPrice(UUID productId, Money price) {
        return setSellingPrice(productId, price, false);
    }

    /**
     * As above, but {@code allowUncosted} lets a caller price stock whose delivery is not yet
     * costed. The pricing workbench does this deliberately: a mixed lot's contents are not known
     * until entered by hand, so an item may be priced before its lot is allocated, accepting that
     * the price may be revisited once the cost is known. The MRP ceiling is never waived — it is a
     * legal limit, not a costing convenience — so it still applies whenever an MRP is recorded.
     */
    @Transactional
    public Product setSellingPrice(UUID productId, Money price, boolean allowUncosted) {
        Product product =
                products.findById(productId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("no such product: " + productId));

        List<Batch> held = batches.findByProductIdNewestFirst(productId);

        // Nothing received yet: no cost to judge against and no MRP to cap at, so the ordinary
        // rules on Product are all that apply. This is how a product priced before its first
        // delivery still works.
        if (!held.isEmpty()) {
            if (!allowUncosted) {
                requireCostKnown(held, productId);
            }
            requireAtOrBelowMrp(held, price);
        }

        // Read before the set: this is the price the journal calls "old", and null here is a
        // first-ever set, not a value to invent (design decision D5 of palletworks-inventory).
        Money oldPrice = product.getSellingPrice();
        product.setSellingPrice(price);
        journal(product, oldPrice, price);
        return product;
    }

    /**
     * Appends the one journal row this price change earns, or none at all.
     *
     * <p>Every price path funnels through {@link #setSellingPrice(UUID, Money, boolean)}, so this
     * is the only place {@link PriceHistory} is ever written — no caller journals on its own and
     * none can bypass the record. A set whose new price equals what the product already carried
     * is a no-op (a re-price that only corrects an MRP or quantity) and writes nothing.
     */
    private void journal(Product product, Money oldPrice, Money newPrice) {
        if (oldPrice != null && oldPrice.paise() == newPrice.paise()) {
            return;
        }
        priceHistory.save(PriceHistory.record(product, oldPrice, newPrice));
    }

    /**
     * Refuses while any delivery of this product is still being unpacked.
     *
     * <p>Deliberately the strictest reading: one open delivery blocks pricing even if an
     * earlier one is settled, because the open one is about to change what the product costs
     * and a price set now would be stale before it was printed.
     */
    private void requireCostKnown(List<Batch> held, UUID productId) {
        for (Batch batch : held) {
            if (!batch.isCosted()) {
                throw new IllegalStateException(
                        "product "
                                + productId
                                + " has stock from a delivery that is still being unpacked, so"
                                + " what it cost is not yet known. Finish and close the delivery"
                                + " before pricing it.");
            }
        }
    }

    /**
     * Refuses a price above the most recently recorded MRP.
     *
     * <p>Measured against the newest batch, because that is the figure printed on the goods
     * now going out and the one a customer can check. Older batches may lawfully carry a lower
     * printed price; they are not what is on the shelf.
     */
    private void requireAtOrBelowMrp(List<Batch> held, Money price) {
        for (Batch batch : held) {
            if (batch.getMrp() != null) {
                if (price.paise() > batch.getMrp().paise()) {
                    throw new IllegalArgumentException(
                            "a selling price of "
                                    + price
                                    + " is above the MRP of "
                                    + batch.getMrp()
                                    + " printed on these goods. Selling above the printed maximum"
                                    + " retail price is unlawful.");
                }
                return;
            }
        }
    }
}
