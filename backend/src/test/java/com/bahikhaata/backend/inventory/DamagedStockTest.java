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

import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Damaged goods are stock worth less, not stock lost.
 *
 * <p>A scratched item sells here, cheaper, and selling it is the business. So it costs what any
 * other unit of the same delivery costs; only what it fetches differs. The earlier model treated
 * a damaged unit as a write-off, which flattered the seconds sale into pure profit and made the
 * sound units look thinner than they were.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-damaged.db")
@Transactional
class DamagedStockTest {

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

    private UUID lotId;

    @BeforeEach
    void importAManifest() {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        "Sushil", "2026-07-17",
                        List.of(new ImportLot("KITCHEN", 100_000, AllocationMethod.RELATIVE_MRP,
                                List.of(new ImportLine("KETTLE", "Kettle", 10, 10_000, null,
                                        "BOX-A", null, null))))));
        lotId = lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b)
                .orElseThrow().getId();
    }

    private ExpectedLine line() {
        return expectedLines.findByLotIdOrderByCode(lotId).get(0);
    }

    private Batch batch(StockCondition condition) {
        return batches
                .findByLotIdAndProductIdAndCondition(
                        lotId, line().getProduct().getId(), condition)
                .orElseThrow();
    }

    /** Drives every carton receipt to a terminal state so the receiving guard permits a close. */
    private void finishReceiving() {
        for (Box box : boxes.findByLotIdOrderByTrackingNumber(lotId)) {
            String carton = box.getTrackingNumber();
            receiving.receiveBox(lotId, carton, AT);
            receiving.markBoxUnpacking(lotId, carton);
            receiving.markBoxUnpacked(lotId, carton);
        }
    }

    @Test
    @DisplayName("Sound and damaged units of one product are held as separate stock")
    void conditionsAreHeldApart() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 8, Money.ofPaise(50_000), false, AT);
        counting.countExpected(line().getId(), StockCondition.DAMAGED, 2, null, false, AT);

        assertThat(batches.findByLotIdAndProductId(lotId, line().getProduct().getId()))
                .as("selling a damaged unit must draw from the damaged pile, which needs its"
                        + " own row for the ledger to point at")
                .hasSize(2);
        assertThat(batch(StockCondition.GOOD).getQuantityReceived()).isEqualTo(8);
        assertThat(batch(StockCondition.DAMAGED).getQuantityReceived()).isEqualTo(2);
        assertThat(stock.onHand(line().getProduct().getId()))
                .as("all ten arrived and all ten are held")
                .isEqualTo(10);
    }

    @Test
    @DisplayName("A damaged unit counts against the expectation, so the line is not short")
    void damagedUnitsAreNotAShortfall() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 8, null, false, AT);
        counting.countExpected(line().getId(), StockCondition.DAMAGED, 2, null, false, AT);

        assertThat(line().getQuantityCounted()).isEqualTo(10);
        assertThat(line().getDiscrepancy())
                .as("a scratched item still arrived; the delivery is complete")
                .isZero();
    }

    @Test
    @DisplayName("Damaged stock costs the same as sound stock from the same delivery")
    void damagedStockCarriesOrdinaryCost() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 8, null, false, AT);
        counting.countExpected(line().getId(), StockCondition.DAMAGED, 2, null, false, AT);

        // Both are pinned at receipt from the line's stated per-unit value; no close is needed.
        assertThat(batch(StockCondition.DAMAGED).getAllocatedUnitCost())
                .as("they cost the same to buy; only what they fetch differs")
                .isEqualTo(batch(StockCondition.GOOD).getAllocatedUnitCost());
        assertThat(batch(StockCondition.DAMAGED).getAllocatedUnitCost().paise())
                .as("and it is a real cost, not zero — a zero would make the seconds sale read"
                        + " as pure profit")
                .isEqualTo(10_000);
        assertThat(batch(StockCondition.GOOD).getAllocatedTotal().paise()
                        + batch(StockCondition.DAMAGED).getAllocatedTotal().paise())
                .as("ten units at their stated 10,000 each — the whole amount paid for the lot")
                .isEqualTo(100_000);
    }

    @Test
    @DisplayName("Damaged goods sell at their own price, set apart from the ordinary one")
    void damagedGoodsHaveTheirOwnPrice() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 8, Money.ofPaise(50_000), false, AT);
        counting.countExpected(line().getId(), StockCondition.DAMAGED, 2, null, false, AT);

        var product = barcodes.findByCode("KETTLE").orElseThrow().getProduct();
        product.setSellingPrice(Money.ofPaise(30_000));

        assertThat(batch(StockCondition.DAMAGED).sellingPrice())
                .as("nobody has decided what the scratched ones are worth yet")
                .isNull();

        product.setDamagedSellingPrice(Money.ofPaise(18_000));

        assertThat(batch(StockCondition.DAMAGED).sellingPrice()).isEqualTo(Money.ofPaise(18_000));
        assertThat(batch(StockCondition.GOOD).sellingPrice())
                .as("pricing the seconds must not touch what sound goods cost a customer")
                .isEqualTo(Money.ofPaise(30_000));
    }

    @Test
    @DisplayName("Goods fit for nothing never become stock")
    void unusableGoodsAreNotStock() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 8, null, false, AT);
        counting.countExpected(line().getId(), StockCondition.UNUSABLE, 2, null, false, AT);

        assertThat(batch(StockCondition.UNUSABLE).getQuantityReceived())
                .as("they arrived, and that is worth recording")
                .isEqualTo(2);
        assertThat(stock.onHand(line().getProduct().getId()))
                .as("but the ledger holds stock that exists to be sold, and these never were")
                .isEqualTo(8);
    }

    @Test
    @DisplayName("Sound goods carry only their own stated cost; scrap is left out of what closed")
    void unusableGoodsAreExcludedFromWhatClosed() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 8, null, false, AT);
        counting.countExpected(line().getId(), StockCondition.UNUSABLE, 2, null, false, AT);

        finishReceiving();
        var closed = closing.close(lotId, false, AT);

        // Cost is pinned per unit at receipt, not a pot spread over the survivors: each sound unit
        // keeps its own stated 10,000, so the eight come to 80,000 — not the whole amount paid.
        assertThat(batch(StockCondition.GOOD).getAllocatedUnitCost().paise()).isEqualTo(10_000);
        assertThat(batch(StockCondition.GOOD).getAllocatedTotal()).isEqualTo(Money.ofPaise(80_000));
        // The scrap never became sellable stock, so closing leaves it out of what it reports as
        // received and costed — only the eight sound units count.
        assertThat(closed.batchesCosted()).as("only the sound batch").isEqualTo(1);
        assertThat(closed.unitsCosted()).as("the two scrap units are not counted").isEqualTo(8);
    }

    @Test
    @DisplayName("How much of a delivery was scrap stays answerable")
    void scrapRemainsVisible() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 7, null, false, AT);
        counting.countExpected(line().getId(), StockCondition.DAMAGED, 1, null, false, AT);
        counting.countExpected(line().getId(), StockCondition.UNUSABLE, 2, null, false, AT);

        // The scrap holds no sellable stock; its count must still not erase the fact. This is what
        // tells a good supplier from a bad one later.
        assertThat(batches.findByLotId(lotId).stream()
                        .filter(b -> b.getCondition() == StockCondition.UNUSABLE)
                        .mapToLong(Batch::getQuantityReceived).sum())
                .isEqualTo(2);
        assertThat(line().getQuantityCounted())
                .as("all ten arrived, whatever state they were in")
                .isEqualTo(10);
    }

    @Test
    @DisplayName("A delivery of nothing but scrap closes, reporting nothing costed")
    void allScrapClosesWithNothingCosted() {
        counting.countExpected(line().getId(), StockCondition.UNUSABLE, 3, null, false, AT);

        finishReceiving();
        // Closing settles no cost now, so an all-scrap delivery is not refused: it simply closes
        // as a completeness marker with nothing sellable and nothing to report as costed.
        var closed = closing.close(lotId, false, AT);

        assertThat(closed.batchesCosted()).as("scrap is excluded from what closed").isZero();
        assertThat(closed.unitsCosted()).isZero();
        assertThat(lots.findById(lotId).orElseThrow().isOpen()).isFalse();
    }

    @Test
    @DisplayName("The MRP is asked once per product, whatever condition the units are in")
    void mrpIsAskedOncePerProduct() {
        counting.countExpected(line().getId(), StockCondition.DAMAGED, 1, Money.ofPaise(50_000), false, AT);

        assertThat(counting.linesIn(line().getBox().getId()).get(0).needsMrp())
                .as("a dented box carries the same printed price as a clean one")
                .isFalse();
    }

    @Test
    @DisplayName("The once-asked MRP reaches the condition that skipped the prompt")
    void theSharedMrpReachesEveryCondition() {
        // DAMAGED is counted first and carries the printed price. GOOD then counts with none,
        // because needsMrp is already false — the very case that used to leave sound stock
        // silently unpriced. It must take the product's MRP, not fall through empty.
        counting.countExpected(line().getId(), StockCondition.DAMAGED, 1, Money.ofPaise(50_000), false, AT);
        counting.countExpected(line().getId(), StockCondition.GOOD, 8, null, false, AT);

        assertThat(batch(StockCondition.GOOD).getMrp())
                .as("a dented pack and a clean one share one printed price")
                .isEqualTo(Money.ofPaise(50_000));
        assertThat(batch(StockCondition.GOOD).isMrpEstimate()).isFalse();
    }

    @Test
    @DisplayName("An estimated MRP stays an estimate when it reaches the other condition")
    void inheritedMrpKeepsItsEstimateFlag() {
        counting.countExpected(line().getId(), StockCondition.DAMAGED, 1, Money.ofPaise(50_000), true, AT);
        counting.countExpected(line().getId(), StockCondition.GOOD, 8, null, false, AT);

        assertThat(batch(StockCondition.GOOD).getMrp()).isEqualTo(Money.ofPaise(50_000));
        assertThat(batch(StockCondition.GOOD).isMrpEstimate())
                .as("a figure looked up, not read off the goods, stays marked so even when shared")
                .isTrue();
    }
}
