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
package com.bahikhaata.backend.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.backend.inventory.GoodsInService;
import com.bahikhaata.backend.inventory.Supplier;
import com.bahikhaata.backend.inventory.SupplierRepository;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.ReceiveLotLine;
import com.bahikhaata.contracts.ReceiveLotRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-price-suggestions.db")
@Transactional
class PriceSuggestionsTest {

    @Autowired
    private ProductRepository products;

    @Autowired
    private GoodsInService goodsIn;

    @Autowired
    private PriceSuggestions suggestions;

    @Autowired
    private SupplierRepository suppliers;

    private String supplierId(String name) {
        return suppliers.findByNameNormalized(Supplier.normalize(name))
                .map(Supplier::getId)
                .orElseGet(() -> suppliers.save(new Supplier(name, null, null, null, null, null)).getId())
                .toString();
    }

    private Product newProduct(String name, Category category) {
        return products.save(new Product(name, category, Map.of()));
    }

    /** Receives {@code quantity} of a product for a lot amount, giving a known unit cost. */
    private void receive(Product product, long quantity, long lotPaise, long mrpPaise) {
        goodsIn.receive(
                new ReceiveLotRequest(
                        supplierId("Liquidator A"),
                        "2026-07-20",
                        lotPaise,
                        0,
                        List.of(
                                new ReceiveLotLine(
                                        product.getId().toString(),
                                        quantity, 0, mrpPaise, false, null))));
    }

    @Test
    @DisplayName("An unpriced product with stock gets a suggestion at its category's margin")
    void suggestsAtCategoryMargin() {
        Product kettle = newProduct("Electric kettle", Category.of("ELECTRONICS"));
        // ₹1,875 for 100 units: ₹18.75 each.
        receive(kettle, 100, 1_875_00, 40_00);

        var suggestion = suggestions.suggestFor(kettle.getId(), null).orElseThrow();

        // Electronics is configured at 20%: 18.75 ÷ 0.8 = ₹23.44 (rounded up).
        assertThat(suggestion.targetMarginPercent()).isEqualTo(20);
        assertThat(suggestion.unitCost()).isEqualTo(Money.ofPaise(1_875));
        assertThat(suggestion.suggestedPrice()).isEqualTo(Money.ofPaise(2_344));
    }

    @Test
    @DisplayName("A custom margin overrides the category's, for that suggestion only")
    void customMarginOverrides() {
        Product kettle = newProduct("Electric kettle", Category.of("ELECTRONICS"));
        receive(kettle, 100, 1_875_00, 40_00);

        var suggestion = suggestions.suggestFor(kettle.getId(), 50).orElseThrow();

        // 18.75 ÷ 0.5 = ₹37.50.
        assertThat(suggestion.targetMarginPercent()).isEqualTo(50);
        assertThat(suggestion.suggestedPrice()).isEqualTo(Money.ofPaise(3_750));
    }

    @Test
    @DisplayName("Asking for a suggestion changes nothing")
    void suggestingIsNotSetting() {
        Product kettle = newProduct("Electric kettle", Category.of("ELECTRONICS"));
        receive(kettle, 100, 1_875_00, 40_00);

        suggestions.suggestFor(kettle.getId(), null);

        // The system computes; a human decides. Until someone sets a price the product stays
        // unpriced, and therefore unsellable.
        Product reloaded = products.findById(kettle.getId()).orElseThrow();
        assertThat(reloaded.isPriced()).isFalse();
        assertThat(reloaded.getSellingPrice()).isNull();
    }

    @Test
    @DisplayName("A product that already has a price gets no suggestion")
    void pricedProductGetsNoSuggestion() {
        Product kettle = newProduct("Electric kettle", Category.of("ELECTRONICS"));
        receive(kettle, 100, 1_875_00, 40_00);
        kettle.setSellingPrice(Money.ofRupees(30));
        products.save(kettle);

        // Repricing on arrival would invalidate every printed sticker the moment it fired.
        assertThat(suggestions.suggestFor(kettle.getId(), null)).isEmpty();
    }

    @Test
    @DisplayName("A product with no stock yet gets no suggestion")
    void noStockNoSuggestion() {
        Product kettle = newProduct("Never received", Category.of("ELECTRONICS"));

        // Nothing has been bought, so there is no cost to work a price out from.
        assertThat(suggestions.suggestFor(kettle.getId(), null)).isEmpty();
    }

    @Test
    @DisplayName("An unknown product gets no suggestion")
    void unknownProductGetsNoSuggestion() {
        assertThat(suggestions.suggestFor(UUID.randomUUID(), null)).isEmpty();
    }

    @Test
    @DisplayName("The suggestion is costed from the most recent delivery, not the oldest")
    void costedFromTheLatestDelivery() {
        Product bottle = newProduct("Steel bottle", Category.of("KITCHEN"));
        // An old cheap delivery, then a recent dearer one.
        goodsIn.receive(
                new ReceiveLotRequest(
                        supplierId("Liquidator A"), "2026-05-01", 1_000_00, 0,
                        List.of(new ReceiveLotLine(bottle.getId().toString(), 100, 0, 40_00, false, null))));
        goodsIn.receive(
                new ReceiveLotRequest(
                        supplierId("Liquidator A"), "2026-07-20", 2_000_00, 0,
                        List.of(new ReceiveLotLine(bottle.getId().toString(), 100, 0, 40_00, false, null))));

        var suggestion = suggestions.suggestFor(bottle.getId(), null).orElseThrow();

        // The latest cost is the best evidence of what the product costs to buy now, which is
        // what a price should be set against — ₹20, not the ₹10 of the old lot.
        assertThat(suggestion.unitCost()).isEqualTo(Money.ofRupees(20));
    }

    @Test
    @DisplayName("The suggestion carries its workings, including the MRP to compare against")
    void suggestionCarriesItsWorkings() {
        Product kettle = newProduct("Electric kettle", Category.of("ELECTRONICS"));
        receive(kettle, 100, 1_875_00, 40_00);

        var suggestion = suggestions.suggestFor(kettle.getId(), null).orElseThrow();

        // A bare number invites either blind acceptance or distrust; the workings let a
        // manager judge it.
        assertThat(suggestion.unitCost()).isEqualTo(Money.ofPaise(1_875));
        assertThat(suggestion.targetMarginPercent()).isEqualTo(20);
        assertThat(suggestion.mrp()).isEqualTo(Money.ofRupees(40));
        assertThat(suggestion.mrpIsEstimate()).isFalse();
    }
}
