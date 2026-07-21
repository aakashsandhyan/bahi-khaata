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
 * Settling what a delivery cost.
 *
 * <p>These assertions deliberately compare lines with one another rather than checking that the
 * shares add up. A quantity fault once survived a 2,000-lot property test and a real import of
 * 3,583 units precisely because the totals were always right: shares of the amount paid sum to
 * the amount paid however wrongly they are split. Only the split can reveal a split.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-lot-closing.db")
@Transactional
class LotClosingTest {

    private static final Instant AT = Instant.parse("2026-07-21T09:00:00Z");

    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private LotClosing closing;
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

    @Test
    @DisplayName("Two lines of equal unit value get equal unit cost, whatever their quantities")
    void quantityDoesNotCompound() {
        UUID lot = importLot(50_000, List.of(
                line("BOX-1", "ONE", 1, 10_000),
                line("BOX-1", "FOUR", 4, 10_000)));
        count(lot, "ONE", 1);
        count(lot, "FOUR", 4);

        closing.close(lot, false, AT);

        assertThat(batchFor(lot, "FOUR").getAllocatedUnitCost())
                .as("four of a thing cost four times one; a differing unit cost means quantity"
                        + " was applied twice")
                .isEqualTo(batchFor(lot, "ONE").getAllocatedUnitCost());
    }

    @Test
    @DisplayName("A uniform fraction of stated value reaches every line")
    void uniformFactorReachesEveryLine() {
        // Paying a quarter of the stated value must make every line cost a quarter of its own,
        // not merely make the lot come to a quarter of the whole.
        UUID lot = importLot(150_000, List.of(
                line("BOX-1", "DEAR", 1, 400_000),
                line("BOX-1", "MANY", 4, 50_000)));
        count(lot, "DEAR", 1);
        count(lot, "MANY", 4);

        closing.close(lot, false, AT);

        assertThat(batchFor(lot, "DEAR").getAllocatedUnitCost()).isEqualTo(Money.ofPaise(100_000));
        assertThat(batchFor(lot, "MANY").getAllocatedUnitCost()).isEqualTo(Money.ofPaise(12_500));
    }

    @Test
    @DisplayName("Shares sum exactly to what was paid")
    void sharesReconcileExactly() {
        UUID lot = importLot(100_000, List.of(
                line("BOX-1", "A", 3, 7_777),
                line("BOX-1", "B", 11, 1_234),
                line("BOX-2", "C", 2, 99_999)));
        count(lot, "A", 3);
        count(lot, "B", 11);
        count(lot, "C", 2);

        closing.close(lot, false, AT);

        long allocated = batches.findByLotId(lot).stream()
                .mapToLong(b -> b.getAllocatedTotal().paise()).sum();
        assertThat(allocated).isEqualTo(100_000);
    }

    @Test
    @DisplayName("A shortfall raises the cost of the units that did arrive")
    void shortfallRaisesUnitCost() {
        UUID lot = importLot(100_000, List.of(
                line("BOX-1", "SHORT", 10, 10_000),
                line("BOX-1", "FULL", 10, 10_000)));
        count(lot, "SHORT", 5);
        count(lot, "FULL", 10);

        closing.close(lot, false, AT);

        // 15 units arrived, all of equal stated value, so each carries a fifteenth of 100,000.
        assertThat(batchFor(lot, "SHORT").getAllocatedUnitCost())
                .isEqualTo(batchFor(lot, "FULL").getAllocatedUnitCost());
        assertThat(batchFor(lot, "SHORT").getAllocatedUnitCost().paise())
                .as("fewer units carrying the same money means each costs more than the 5,000"
                        + " it would have at the expected 20")
                .isGreaterThan(5_000);
        assertThat(batchFor(lot, "SHORT").getAllocatedTotal().paise()
                        + batchFor(lot, "FULL").getAllocatedTotal().paise())
                .isEqualTo(100_000);
    }

    @Test
    @DisplayName("Lines never counted receive no share at all")
    void uncountedLinesGetNothing() {
        UUID lot = importLot(100_000, List.of(
                line("BOX-1", "CAME", 4, 10_000),
                line("BOX-2", "NEVER", 4, 10_000)));
        count(lot, "CAME", 4);

        closing.close(lot, true, AT);

        assertThat(batchFor(lot, "CAME").getAllocatedTotal()).isEqualTo(Money.ofPaise(100_000));
        assertThat(barcodes.findByCode("NEVER")).isPresent();
        assertThat(batches.findByLotIdAndProductId(
                        lot, barcodes.findByCode("NEVER").orElseThrow().getProduct().getId()))
                .as("goods that never arrived hold no stock and carry no cost")
                .isEmpty();
    }

    @Test
    @DisplayName("Goods nobody listed are weighed at the lot average and marked estimated")
    void unlistedGoodsAreWeighedAtTheLotAverage() {
        UUID lot = importLot(100_000, List.of(line("BOX-1", "KNOWN", 4, 10_000)));
        count(lot, "KNOWN", 4);
        UUID boxId = lineFor(lot, "KNOWN").getBox().getId();
        counting.countUnlisted(boxId, "SURPRISE", "Surprise", "KITCHEN", 1, null, false, AT);

        closing.close(lot, false, AT);

        Batch surprise = batchFor(lot, "SURPRISE");
        assertThat(surprise.getCostBasis())
                .as("weighed at an average rather than a stated figure, and recorded as such")
                .isEqualTo(CostBasis.ESTIMATED);
        assertThat(batchFor(lot, "KNOWN").getCostBasis()).isEqualTo(CostBasis.ALLOCATED);
        assertThat(surprise.getAllocatedUnitCost())
                .as("the average of the named lines is their own unit value here")
                .isEqualTo(batchFor(lot, "KNOWN").getAllocatedUnitCost());
        assertThat(surprise.getAllocatedTotal().paise()
                        + batchFor(lot, "KNOWN").getAllocatedTotal().paise())
                .isEqualTo(100_000);
    }

    @Test
    @DisplayName("Closing a lot in which nothing states a value is refused")
    void noStatedValueAnywhereIsRefused() {
        UUID lot = importLot(100_000, List.of(line("BOX-1", "NOVALUE", 2, 0)));
        count(lot, "NOVALUE", 2);

        assertThatThrownBy(() -> closing.close(lot, false, AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no line states a value");
        assertThat(batchFor(lot, "NOVALUE").isCosted()).isFalse();
        assertThat(lots.findById(lot).orElseThrow().getState()).isEqualTo(LotState.OPEN);
    }

    @Test
    @DisplayName("Closing over unopened cartons needs confirmation, and names them")
    void unopenedCartonsNeedConfirmation() {
        UUID lot = importLot(100_000, List.of(
                line("BOX-OPENED", "A", 2, 10_000),
                line("BOX-UNTOUCHED", "B", 2, 10_000)));
        count(lot, "A", 2);

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
    @DisplayName("Valuation reports uncosted stock apart rather than counting it at zero")
    void valuationSeparatesUncostedStock() {
        UUID lot = importLot(100_000, List.of(line("BOX-1", "HELD", 4, 10_000)));
        count(lot, "HELD", 4);
        UUID productId = barcodes.findByCode("HELD").orElseThrow().getProduct().getId();

        var open = stock.valuationDetailAsAt(productId, AT.plusSeconds(60));
        assertThat(open.valued()).isEqualTo(Money.ZERO);
        assertThat(open.uncostedUnits())
                .as("four units are genuinely held; their cost is simply not yet known")
                .isEqualTo(4);
        assertThat(open.isComplete()).isFalse();

        closing.close(lot, false, AT);

        var closed = stock.valuationDetailAsAt(productId, AT.plusSeconds(60));
        assertThat(closed.valued()).isEqualTo(Money.ofPaise(100_000));
        assertThat(closed.uncostedUnits()).isZero();
        assertThat(closed.isComplete()).isTrue();
    }
}
