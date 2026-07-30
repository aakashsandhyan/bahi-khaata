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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.LotState;
import com.bahikhaata.contracts.Money;
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
 * Costing a delivery, and closing it.
 *
 * <p>Cost is now pinned per product at receipt, not apportioned at close: each product is costed
 * from its manifest line's stated per-unit value the moment it is counted, so a batch is costed
 * and priceable while its lot is still open. Closing settles nothing — it is a
 * receiving-completeness marker that reports any goods that arrived without a stated cost (an
 * uncosted surplus) rather than inventing a figure for them.
 *
 * <p>These assertions deliberately compare lines with one another rather than checking that the
 * shares add up. A quantity fault once survived a 2,000-lot property test and a real import of
 * 3,583 units precisely because the totals were always right. Under pinning each line stands on
 * its own stated value, so the split is checked line by line.
 *
 * <p>The cost pinned is the product's stated value scaled by the rate the lot was bought at —
 * amount paid over the total stated value, one rate per category-lot. Most fixtures here buy the
 * lot at exactly its stated total, so that rate is 1.0 and the pinned cost equals the stated
 * value, which keeps the independence, shortfall and quantity checks readable. {@link
 * #costScalesByTheLotRate} exercises a rate below one (a returns discount) and above one (a
 * supply markup) on its own.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-lot-closing.db")
@Transactional
class LotClosingTest {

    private static final Instant AT = Instant.parse("2026-07-21T09:00:00Z");

    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private LotClosing closing;
    @Autowired private ReceivingService receiving;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BatchRepository batches;
    @Autowired private BoxRepository boxes;
    @Autowired private BarcodeRepository barcodes;
    @Autowired private LotRepository lots;
    @Autowired private StockLevels stock;

    /** Imports one lot and returns its id. Each line is (box, code, quantity, unit value). */
    private UUID importLot(long paidPaise, List<ImportLine> lines) {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        "Sushil", "2026-07-17",
                        List.of(new ImportLot("KITCHEN", paidPaise,
                                AllocationMethod.RELATIVE_MRP, lines))));
        return lots.findAll().stream()
                .filter(Lot::isOpen).reduce((a, b) -> b).orElseThrow().getId();
    }

    private static ImportLine line(String box, String code, long qty, long unitValuePaise) {
        return new ImportLine(code, code, qty, unitValuePaise, null, box, null, null);
    }

    private ExpectedLine lineFor(UUID lotId, String code) {
        return expectedLines.findByLotIdOrderByCode(lotId).stream()
                .filter(l -> l.getCode().equals(code)).findFirst().orElseThrow();
    }

    private void count(UUID lotId, String code, long quantity) {
        counting.countExpected(lineFor(lotId, code).getId(), quantity, null, false, AT);
    }

    private Batch batchFor(UUID lotId, String code) {
        UUID productId = barcodes.findByCode(code).orElseThrow().getProduct().getId();
        return batches
                .findByLotIdAndProductIdAndCondition(lotId, productId, StockCondition.GOOD)
                .orElseThrow();
    }

    /**
     * Drives every carton receipt to a terminal state so the lot's receiving guard permits a
     * close. Closing no longer settles cost — it only records that receiving is done — but the
     * guard still requires every carton to have been dealt with.
     */
    private void finishReceiving(UUID lotId) {
        for (Box box : boxes.findByLotIdOrderByTrackingNumber(lotId)) {
            String carton = box.getTrackingNumber();
            receiving.receiveBox(lotId, carton, AT);
            receiving.markBoxUnpacking(lotId, carton);
            receiving.markBoxUnpacked(lotId, carton);
        }
    }

    @Test
    @DisplayName("Two lines of equal unit value get equal unit cost, whatever their quantities")
    void quantityDoesNotCompound() {
        UUID lot = importLot(50_000, List.of(
                line("BOX-1", "ONE", 1, 10_000),
                line("BOX-1", "FOUR", 4, 10_000)));
        count(lot, "ONE", 1);
        count(lot, "FOUR", 4);

        // Cost is pinned at receipt from the stated unit value — no lot close needed. Four of a
        // thing cost four times one; a differing unit cost would mean quantity was applied twice.
        assertThat(batchFor(lot, "FOUR").getAllocatedUnitCost())
                .isEqualTo(batchFor(lot, "ONE").getAllocatedUnitCost());
        assertThat(batchFor(lot, "ONE").getAllocatedUnitCost()).isEqualTo(Money.ofPaise(10_000));
        assertThat(batchFor(lot, "ONE").isCosted()).isTrue();
        assertThat(batchFor(lot, "ONE").getCostBasis()).isEqualTo(CostBasis.PINNED);
    }

    @Test
    @DisplayName("Each line is costed at its own stated per-unit value")
    void eachLineKeepsItsStatedUnitCost() {
        // Bought at exactly its stated total (rate 1.0), so each line's cost is its own stated
        // value: no averaging across the lines. A dear line stays dear and a cheap one cheap.
        UUID lot = importLot(600_000, List.of(
                line("BOX-1", "DEAR", 1, 400_000),
                line("BOX-1", "MANY", 4, 50_000)));
        count(lot, "DEAR", 1);
        count(lot, "MANY", 4);

        assertThat(batchFor(lot, "DEAR").getAllocatedUnitCost()).isEqualTo(Money.ofPaise(400_000));
        assertThat(batchFor(lot, "MANY").getAllocatedUnitCost()).isEqualTo(Money.ofPaise(50_000));
    }

    @Test
    @DisplayName("Cost is the stated value scaled by the rate the lot was bought at")
    void costScalesByTheLotRate() {
        // A returns lot bought at a quarter of its stated online value: every unit is costed at
        // a quarter of its own price, uniformly — the rate is per category, not an average that
        // smears a dear line into a cheap one.
        UUID discounted = importLot(50_000, List.of( // paid 50,000 of 200,000 stated -> rate 0.25
                line("BOX-1", "PRICEY", 1, 120_000),
                line("BOX-1", "CHEAP", 8, 10_000)));
        count(discounted, "PRICEY", 1);
        count(discounted, "CHEAP", 8);
        assertThat(batchFor(discounted, "PRICEY").getAllocatedUnitCost())
                .isEqualTo(Money.ofPaise(30_000));
        assertThat(batchFor(discounted, "CHEAP").getAllocatedUnitCost())
                .isEqualTo(Money.ofPaise(2_500));

        // Kitchen and footwear arrive the other way: the stated value is the supplier's own cost
        // and the lot is paid above it — a rate over one, the 110% markup.
        UUID markedUp = importLot(110_000, List.of( // paid 110,000 of 100,000 stated -> rate 1.10
                line("BOX-2", "POT", 10, 10_000)));
        count(markedUp, "POT", 10);
        assertThat(batchFor(markedUp, "POT").getAllocatedUnitCost())
                .as("comes in at 110% of the supplier's stated cost")
                .isEqualTo(Money.ofPaise(11_000));
    }

    @Test
    @DisplayName("Each batch's total is its stated unit value times its quantity, independently")
    void eachBatchCostedIndependently() {
        UUID lot = importLot(236_903, List.of( // paid == stated total, so the rate is 1.0
                line("BOX-1", "A", 3, 7_777),
                line("BOX-1", "B", 11, 1_234),
                line("BOX-2", "C", 2, 99_999)));
        count(lot, "A", 3);
        count(lot, "B", 11);
        count(lot, "C", 2);

        // Each line stands on its own stated value; the amount paid apportions nothing.
        assertThat(batchFor(lot, "A").getAllocatedTotal()).isEqualTo(Money.ofPaise(3 * 7_777));
        assertThat(batchFor(lot, "B").getAllocatedTotal()).isEqualTo(Money.ofPaise(11 * 1_234));
        assertThat(batchFor(lot, "C").getAllocatedTotal()).isEqualTo(Money.ofPaise(2 * 99_999));
    }

    @Test
    @DisplayName("A shortfall changes nothing — each unit keeps its stated cost")
    void shortfallChangesNothing() {
        UUID lot = importLot(200_000, List.of( // paid == stated total over expected, rate 1.0
                line("BOX-1", "SHORT", 10, 10_000),
                line("BOX-1", "FULL", 10, 10_000)));
        count(lot, "SHORT", 5);
        count(lot, "FULL", 10);

        // The units that did not arrive do not raise the cost of the ones that did: cost is
        // pinned per unit, not a share of a fixed pot spread over fewer survivors.
        assertThat(batchFor(lot, "SHORT").getAllocatedUnitCost())
                .isEqualTo(batchFor(lot, "FULL").getAllocatedUnitCost());
        assertThat(batchFor(lot, "SHORT").getAllocatedUnitCost()).isEqualTo(Money.ofPaise(10_000));
        assertThat(batchFor(lot, "SHORT").getAllocatedTotal().paise()).isEqualTo(5 * 10_000);
        assertThat(batchFor(lot, "FULL").getAllocatedTotal().paise()).isEqualTo(10 * 10_000);
    }

    @Test
    @DisplayName("Lines never counted receive no batch and no cost")
    void uncountedLinesGetNothing() {
        UUID lot = importLot(80_000, List.of( // paid == stated total over expected, rate 1.0
                line("BOX-1", "CAME", 4, 10_000),
                line("BOX-2", "NEVER", 4, 10_000)));
        count(lot, "CAME", 4);

        finishReceiving(lot);
        closing.close(lot, true, AT);

        assertThat(batchFor(lot, "CAME").getAllocatedTotal()).isEqualTo(Money.ofPaise(40_000));
        assertThat(barcodes.findByCode("NEVER")).isPresent();
        assertThat(batches.findByLotIdAndProductId(
                        lot, barcodes.findByCode("NEVER").orElseThrow().getProduct().getId()))
                .as("goods that never arrived hold no stock and carry no cost")
                .isEmpty();
    }

    @Test
    @DisplayName("Goods nobody listed stay uncosted — no lot-average estimate is invented")
    void unlistedGoodsStayUncosted() {
        UUID lot = importLot(40_000, List.of(line("BOX-1", "KNOWN", 4, 10_000)));
        count(lot, "KNOWN", 4);
        UUID boxId = lineFor(lot, "KNOWN").getBox().getId();
        counting.countUnlisted(boxId, "SURPRISE", "Surprise", "KITCHEN", 1, null, false, AT);

        finishReceiving(lot);
        closing.close(lot, false, AT);

        Batch surprise = batchFor(lot, "SURPRISE");
        assertThat(surprise.isCosted())
                .as("a surplus carries no stated value and is not weighed at any average; it stays"
                        + " genuinely uncosted rather than being passed off as a figure")
                .isFalse();
        assertThat(batchFor(lot, "KNOWN").getCostBasis())
                .as("the listed line was pinned at receipt from its stated value")
                .isEqualTo(CostBasis.PINNED);
        assertThat(batchFor(lot, "KNOWN").getAllocatedUnitCost()).isEqualTo(Money.ofPaise(10_000));
    }

    @Test
    @DisplayName("Closing a lot whose goods state no value succeeds, leaving them uncosted")
    void noStatedValueLeavesGoodsUncosted() {
        UUID lot = importLot(100_000, List.of(line("BOX-1", "NOVALUE", 2, 0)));
        count(lot, "NOVALUE", 2);

        finishReceiving(lot);
        // Closing no longer refuses a lot that states no value; it is a completeness marker that
        // settles no cost. The goods stay uncosted and are reported as a surplus.
        var outcome = closing.close(lot, false, AT);

        assertThat(batchFor(lot, "NOVALUE").isCosted()).isFalse();
        assertThat(outcome.batchesWeighedAtLotAverage())
                .as("the uncosted surplus is counted so an uncosted tail is visible, not silent")
                .isEqualTo(1);
        assertThat(lots.findById(lot).orElseThrow().getState()).isEqualTo(LotState.CLOSED);
    }

    @Test
    @DisplayName("Closing over unopened cartons needs confirmation, and names them")
    void unopenedCartonsNeedConfirmation() {
        UUID lot = importLot(100_000, List.of(
                line("BOX-OPENED", "A", 2, 10_000),
                line("BOX-UNTOUCHED", "B", 2, 10_000)));
        count(lot, "A", 2);

        finishReceiving(lot);
        assertThatThrownBy(() -> closing.close(lot, false, AT))
                .isInstanceOf(LotClosing.UnopenedCartonsException.class)
                .hasMessageContaining("BOX-UNTOUCHED");
        assertThat(lots.findById(lot).orElseThrow().isOpen()).isTrue();

        var outcome = closing.close(lot, true, AT);

        assertThat(outcome.unopenedCartons()).containsExactly("BOX-UNTOUCHED");
        assertThat(lots.findById(lot).orElseThrow().getState()).isEqualTo(LotState.CLOSED);
    }

    @Test
    @DisplayName("A closed lot refuses further counts, and keeps the costs it settled")
    void closedLotIsFinal() {
        UUID lot = importLot(100_000, List.of(line("BOX-1", "DONE", 4, 10_000)));
        count(lot, "DONE", 4);
        finishReceiving(lot);
        closing.close(lot, false, AT);

        Money settled = batchFor(lot, "DONE").getAllocatedTotal();
        UUID lineId = lineFor(lot, "DONE").getId();

        assertThatThrownBy(() -> counting.countExpected(lineId, 1, null, false, AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
        assertThat(batchFor(lot, "DONE").getAllocatedTotal()).isEqualTo(settled);
        assertThatThrownBy(() -> closing.close(lot, false, AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    @DisplayName("Valuation values pinned stock at once and reports uncosted surplus apart")
    void valuationSeparatesUncostedStock() {
        UUID lot = importLot(40_000, List.of( // paid == stated total of the costed line, rate 1.0
                line("BOX-1", "COSTED", 4, 10_000),
                line("BOX-1", "HELD", 4, 0)));
        count(lot, "COSTED", 4);
        count(lot, "HELD", 4);
        UUID costedId = barcodes.findByCode("COSTED").orElseThrow().getProduct().getId();
        UUID heldId = barcodes.findByCode("HELD").orElseThrow().getProduct().getId();

        // Pinned at receipt: valued at once, while the lot is still open.
        var costedOpen = stock.valuationDetailAsAt(costedId, AT.plusSeconds(60));
        assertThat(costedOpen.valued()).isEqualTo(Money.ofPaise(40_000));
        assertThat(costedOpen.uncostedUnits()).isZero();
        assertThat(costedOpen.isComplete()).isTrue();

        // A surplus states no value, so it stays uncosted — reported apart, not counted at zero.
        var heldOpen = stock.valuationDetailAsAt(heldId, AT.plusSeconds(60));
        assertThat(heldOpen.valued()).isEqualTo(Money.ZERO);
        assertThat(heldOpen.uncostedUnits())
                .as("four units are genuinely held; their cost is simply not stated")
                .isEqualTo(4);
        assertThat(heldOpen.isComplete()).isFalse();

        finishReceiving(lot);
        closing.close(lot, false, AT);

        // Closing settles no cost: the pinned stock is unchanged and the surplus stays uncosted.
        var costedClosed = stock.valuationDetailAsAt(costedId, AT.plusSeconds(60));
        assertThat(costedClosed.valued()).isEqualTo(Money.ofPaise(40_000));
        assertThat(costedClosed.isComplete()).isTrue();

        var heldClosed = stock.valuationDetailAsAt(heldId, AT.plusSeconds(60));
        assertThat(heldClosed.valued()).isEqualTo(Money.ZERO);
        assertThat(heldClosed.uncostedUnits())
                .as("closing invents no cost for a surplus that never stated one")
                .isEqualTo(4);
        assertThat(heldClosed.isComplete()).isFalse();
    }
}
