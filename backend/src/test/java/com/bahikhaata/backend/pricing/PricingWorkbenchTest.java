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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.inventory.ConsignmentImporter;
import com.bahikhaata.backend.inventory.ExpectedLine;
import com.bahikhaata.backend.inventory.ExpectedLineRepository;
import com.bahikhaata.backend.inventory.GoodsInCounting;
import com.bahikhaata.backend.inventory.Lot;
import com.bahikhaata.backend.inventory.LotClosing;
import com.bahikhaata.backend.inventory.LotRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.Marketplace;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.PriceProposal;
import com.bahikhaata.contracts.PriceableItem;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deciding what goods sell for.
 *
 * <p>The figures here mirror a real off-market delivery: goods bought at a quarter of what they
 * fetched online. That is why margin is barely the constraint — the interesting lines are the
 * ones a rule pushes above the printed MRP or, in principle, past the online price.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-pricing-workbench.db")
@Transactional
class PricingWorkbenchTest {

    private static final Instant AT = Instant.parse("2026-07-21T09:00:00Z");

    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private LotClosing closing;
    @Autowired private PricingWorkbench workbench;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BarcodeRepository barcodes;
    @Autowired private LotRepository lots;

    /**
     * Receives one HOME_ESSENTIALS product, online at the given price, bought at a quarter of it,
     * counts it, closes the delivery, and returns its product id. Priced and ready.
     */
    private UUID received(String code, long onlinePaise, long mrpPaise) {
        long costPaise = onlinePaise / 4; // the 0.25 factor of a real off-market sheet
        importer.importConsignment(
                new ImportConsignmentRequest(
                        "Sushil", "2026-07-17",
                        List.of(new ImportLot("HOME_ESSENTIALS", costPaise,
                                AllocationMethod.RELATIVE_MRP,
                                List.of(new ImportLine(code, code, 1, onlinePaise, null,
                                        "BOX-" + code, onlinePaise, Marketplace.AMAZON))))));
        UUID lotId = lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b)
                .orElseThrow().getId();
        ExpectedLine line = expectedLines.findByLotIdOrderByCode(lotId).get(0);
        counting.countExpected(line.getId(), StockCondition.GOOD, 1, Money.ofPaise(mrpPaise),
                false, AT);
        closing.close(lotId, false, AT);
        return line.getProduct().getId();
    }

    @Test
    @DisplayName("Only goods from a closed delivery are priceable")
    void openDeliveriesAreNotPriceable() {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        "Sushil", "2026-07-17",
                        List.of(new ImportLot("HOME_ESSENTIALS", 10_000,
                                AllocationMethod.RELATIVE_MRP,
                                List.of(new ImportLine("OPEN", "Still open", 1, 40_000, null,
                                        "BOX-O", null, null))))));
        UUID lotId = lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b)
                .orElseThrow().getId();
        counting.countExpected(
                expectedLines.findByLotIdOrderByCode(lotId).get(0).getId(),
                StockCondition.GOOD, 1, Money.ofPaise(99_900), false, AT);

        assertThat(workbench.priceable("HOME_ESSENTIALS"))
                .as("cost is not settled until the delivery closes; a margin on it is not a margin")
                .noneMatch(item -> item.name().equals("Still open"));
    }

    @Test
    @DisplayName("A priceable item carries cost, online price and MRP together")
    void priceableCarriesTheFactsForADecision() {
        UUID id = received("ITEM1", 40_000, 99_900);

        PriceableItem item =
                workbench.priceable("HOME_ESSENTIALS").stream()
                        .filter(i -> i.productId().equals(id)).findFirst().orElseThrow();

        assertThat(item.unitCostPaise()).as("a quarter of the online price").isEqualTo(10_000);
        assertThat(item.onlinePricePaise()).isEqualTo(40_000);
        assertThat(item.mrpPaise()).isEqualTo(99_900);
        assertThat(item.currentPricePaise()).as("not yet priced").isNull();
    }

    @Test
    @DisplayName("A target margin sets the price from cost, and reports where it lands")
    void marginPricesFromCostAndPlacesIt() {
        UUID id = received("ITEM2", 40_000, 99_900); // cost 10,000

        PriceProposal p = workbench.preview(List.of(id), 60).get(0);

        // 60% margin on a 10,000 cost: price = 10,000 / 0.40 = 25,000.
        assertThat(p.pricePaise()).isEqualTo(25_000);
        assertThat(p.grossMarginPercent()).isEqualTo(60);
        assertThat(p.beatsOnline())
                .as("25,000 is well under the 40,000 it sold for online — the promise holds")
                .isTrue();
        assertThat(p.aboveMrp()).isFalse();
        assertThat(p.percentOfMrp()).as("25,000 of a 99,900 MRP").isEqualTo(25);
    }

    @Test
    @DisplayName("A margin high enough to breach the MRP is flagged, not hidden")
    void aMarginThatBreachesMrpIsFlagged() {
        // Cost 10,000, but a low printed MRP of 12,000. A 60% margin wants 25,000 — above it.
        UUID id = received("ITEM3", 40_000, 12_000);

        PriceProposal p = workbench.preview(List.of(id), 60).get(0);

        assertThat(p.pricePaise()).isEqualTo(25_000);
        assertThat(p.aboveMrp())
                .as("selling above the printed MRP is unlawful; the line needs a person")
                .isTrue();
    }

    @Test
    @DisplayName("Pricing a category prices the unpriced and reports what it left alone")
    void bulkPricesTheUnpricedOnly() {
        UUID a = received("A", 40_000, 99_900);
        received("B", 80_000, 199_900);
        // C's MRP is below what a 60% margin would charge, so it must be left for a person.
        received("C", 40_000, 12_000);

        // A is priced by hand first, to prove bulk does not overwrite it.
        workbench.apply(a, Money.ofPaise(19_900));

        PricingWorkbench.BulkResult result = workbench.applyCategory("HOME_ESSENTIALS", 60);

        assertThat(result.priced()).as("only B").isEqualTo(1);
        assertThat(result.skippedAlreadyPriced()).as("A, set by hand").isEqualTo(1);
        assertThat(result.skippedAboveMrp()).as("C, which needs a person").isEqualTo(1);
    }

    @Test
    @DisplayName("Applying a price refuses to breach the MRP")
    void applyRefusesAboveMrp() {
        UUID id = received("ITEM4", 40_000, 20_000);

        assertThatThrownBy(() -> workbench.apply(id, Money.ofPaise(20_001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unlawful");
    }
}
