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

import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.BoxCountEntry;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.ProductCountRequest;
import com.bahikhaata.contracts.ProductCountResult;
import com.bahikhaata.contracts.ProductLotLines;
import com.bahikhaata.contracts.StockCondition;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counting a product across the boxes of one delivery in a single act — capped, concurrency-checked,
 * and writing through the same path as box-by-box counting.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-product-counting.db")
@Transactional
class ProductCountingTest {

    @Autowired private ConsignmentImporter importer;
    @Autowired private ProductCounting counting;
    @Autowired private GoodsInCounting boxCounting;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BatchRepository batches;
    @Autowired private LotRepository lots;
    @Autowired private BoxReceiptRepository boxReceipts;

    private UUID lotId;
    private UUID productId;

    @BeforeEach
    void importOneProductAcrossThreeBoxes() {
        // The same product (code AAA) on three boxes: 2 + 3 + 1 expected.
        importer.importConsignment(
                new ImportConsignmentRequest(
                        "Sushil", "2026-07-17",
                        List.of(new ImportLot("KITCHEN", 100_000, AllocationMethod.RELATIVE_MRP,
                                List.of(
                                        new ImportLine("AAA", "Alpha Kettle", 2, 10_000, null, "BOX-A", null, null),
                                        new ImportLine("AAA", "Alpha Kettle", 3, 10_000, null, "BOX-B", null, null),
                                        new ImportLine("AAA", "Alpha Kettle", 1, 10_000, null, "BOX-C", null, null))))));
        lotId = lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b).orElseThrow().getId();
        productId = expectedLines.findByLotIdOrderByCode(lotId).get(0).getProduct().getId();
    }

    private ProductBoxLineView line(String tracking) {
        ProductLotLines g = counting.linesFor(lotId, productId);
        return g.lines().stream()
                .filter(l -> l.boxTracking().equals(tracking))
                .map(l -> new ProductBoxLineView(l.lineId(), l.outstanding()))
                .findFirst().orElseThrow();
    }

    private record ProductBoxLineView(UUID lineId, long outstanding) {}

    @Test
    @DisplayName("The grid lists a product's outstanding box-lines for an open lot")
    void gridLinesForOpenLot() {
        ProductLotLines g = counting.linesFor(lotId, productId);
        assertThat(g.lines()).hasSize(3);
        assertThat(g.lines().stream().mapToLong(l -> l.outstanding()).sum()).isEqualTo(6);
        assertThat(g.productName()).isEqualTo("Alpha Kettle");
    }

    @Test
    @DisplayName("A closed lot offers no lines")
    void closedLotEmpty() {
        // Close the lot entity directly — linesFor only cares whether the lot is open, and the
        // receiving-gated close path is a separate concern this feature does not touch.
        Lot lot = lots.findById(lotId).orElseThrow();
        lot.close(java.time.Instant.parse("2026-07-20T09:00:00Z"));
        lots.save(lot);
        assertThat(counting.linesFor(lotId, productId).lines()).isEmpty();
    }

    @Test
    @DisplayName("Per-box counts commit through the shared path; batch and ledger written")
    void countsCommit() {
        var a = line("BOX-A");
        var b = line("BOX-B");
        ProductCountResult r = counting.count(new ProductCountRequest(
                productId, lotId, StockCondition.GOOD, 20_000L, false,
                List.of(
                        new BoxCountEntry(a.lineId(), 2, a.outstanding()),
                        new BoxCountEntry(b.lineId(), 3, b.outstanding()))));

        assertThat(r.linesCounted()).isEqualTo(2);
        assertThat(r.unitsCounted()).isEqualTo(5);
        assertThat(r.rejected()).isEmpty();
        // A GOOD batch now holds the 5 counted, same as box counting would leave it.
        assertThat(batches.findByLotIdAndProductIdAndCondition(lotId, productId, StockCondition.GOOD)
                        .orElseThrow().getQuantityReceived())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("A quantity above outstanding is capped, never over-counts")
    void capsAtOutstanding() {
        var c = line("BOX-C"); // outstanding 1
        ProductCountResult r = counting.count(new ProductCountRequest(
                productId, lotId, StockCondition.GOOD, 20_000L, false,
                List.of(new BoxCountEntry(c.lineId(), 9, c.outstanding()))));

        assertThat(r.unitsCounted()).isEqualTo(1);
        assertThat(counting.linesFor(lotId, productId).lines().stream()
                        .noneMatch(l -> l.boxTracking().equals("BOX-C")))
                .isTrue(); // fully counted, dropped from the grid
    }

    @Test
    @DisplayName("An entry whose outstanding moved since the grid loaded is refused; others commit")
    void concurrencyRefusal() {
        var a = line("BOX-A"); // outstanding 2, remembered
        var b = line("BOX-B"); // outstanding 3
        // Another station counts 1 into BOX-A meanwhile, so its outstanding is now 1, not 2.
        boxCounting.countExpected(a.lineId(), StockCondition.GOOD, 1, Money.ofPaise(20_000), false,
                java.time.Instant.parse("2026-07-18T09:00:00Z"));

        ProductCountResult r = counting.count(new ProductCountRequest(
                productId, lotId, StockCondition.GOOD, 20_000L, false,
                List.of(
                        new BoxCountEntry(a.lineId(), 2, a.outstanding()), // stale (saw 2, now 1)
                        new BoxCountEntry(b.lineId(), 3, b.outstanding()))));

        assertThat(r.rejected()).hasSize(1);
        assertThat(r.rejected().get(0).lineId()).isEqualTo(a.lineId());
        assertThat(r.rejected().get(0).nowOutstanding()).isEqualTo(1);
        // BOX-B still committed.
        assertThat(r.linesCounted()).isEqualTo(1);
        assertThat(r.unitsCounted()).isEqualTo(3);
    }

    @Test
    @DisplayName("A product-centric count leaves the box's received state unchanged")
    void receivingUntouched() {
        var a = line("BOX-A");
        // The receipt starts EXPECTED at import.
        assertThat(boxReceipts.findByLotIdAndManifestCartonId(lotId, "BOX-A").orElseThrow().getState())
                .isEqualTo(com.bahikhaata.contracts.BoxState.EXPECTED);

        counting.count(new ProductCountRequest(
                productId, lotId, StockCondition.GOOD, 20_000L, false,
                List.of(new BoxCountEntry(a.lineId(), 2, a.outstanding()))));

        // Counting does not receive the box — exactly as box-by-box counting leaves it.
        assertThat(boxReceipts.findByLotIdAndManifestCartonId(lotId, "BOX-A").orElseThrow().getState())
                .isEqualTo(com.bahikhaata.contracts.BoxState.EXPECTED);
    }

    @Test
    @DisplayName("A line not belonging to the product and lot is refused")
    void lineGuard() {
        // A different product's line in the same lot.
        importer.importConsignment(
                new ImportConsignmentRequest(
                        "Sushil", "2026-07-17",
                        List.of(new ImportLot("KITCHEN", 100_000, AllocationMethod.RELATIVE_MRP,
                                List.of(new ImportLine("ZZZ", "Other Thing", 1, 10_000, null, "BOX-Z", null, null))))));
        UUID otherLotId = lots.findAll().stream().filter(Lot::isOpen)
                .reduce((x, y) -> y).orElseThrow().getId();
        UUID otherLineId = expectedLines.findByLotIdOrderByCode(otherLotId).get(0).getId();

        assertThatThrownBy(() -> counting.count(new ProductCountRequest(
                productId, lotId, StockCondition.GOOD, 20_000L, false,
                List.of(new BoxCountEntry(otherLineId, 1, 1)))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
