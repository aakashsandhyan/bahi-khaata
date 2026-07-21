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
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BatchRepository batches;
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

        closing.close(lotId, false, AT);

        assertThat(batch(StockCondition.DAMAGED).getAllocatedUnitCost())
                .as("they cost the same to buy; only what they fetch differs")
                .isEqualTo(batch(StockCondition.GOOD).getAllocatedUnitCost());
        assertThat(batch(StockCondition.DAMAGED).getAllocatedUnitCost().paise())
                .as("and it is a real cost, not zero — a zero would make the seconds sale read"
                        + " as pure profit")
                .isEqualTo(10_000);
        assertThat(batch(StockCondition.GOOD).getAllocatedTotal().paise()
                        + batch(StockCondition.DAMAGED).getAllocatedTotal().paise())
                .isEqualTo(100_000);
    }

    @Test
    @DisplayName("Damaged goods sell at their own price, set apart from the ordinary one")
    void damagedGoodsHaveTheirOwnPrice() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 8, Money.ofPaise(50_000), false, AT);
        counting.countExpected(line().getId(), StockCondition.DAMAGED, 2, null, false, AT);
        closing.close(lotId, false, AT);

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
    @DisplayName("The MRP is asked once per product, whatever condition the units are in")
    void mrpIsAskedOncePerProduct() {
        counting.countExpected(line().getId(), StockCondition.DAMAGED, 1, Money.ofPaise(50_000), false, AT);

        assertThat(counting.linesIn(line().getBox().getId()).get(0).needsMrp())
                .as("a dented box carries the same printed price as a clean one")
                .isFalse();
    }
}
