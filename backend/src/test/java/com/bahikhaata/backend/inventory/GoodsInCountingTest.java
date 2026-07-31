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
import com.bahikhaata.contracts.CartonProgress;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Opening cartons and saying what was actually inside them. */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-counting.db")
@Transactional
class GoodsInCountingTest {

    private static final Instant AT = Instant.parse("2026-07-21T09:00:00Z");

    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BatchRepository batches;
    @Autowired private BoxRepository boxes;
    @Autowired private BarcodeRepository barcodes;
    @Autowired private StockLevels stock;
    @Autowired private LotRepository lots;
    @Autowired private SupplierRepository suppliers;

    private UUID lotId;

    private String supplierId(String name) {
        return suppliers.findByNameNormalized(Supplier.normalize(name))
                .map(Supplier::getId)
                .orElseGet(() -> suppliers.save(new Supplier(name, null, null, null, null, null)).getId())
                .toString();
    }

    @BeforeEach
    void importAManifest() {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Sushil"),
                        "2026-07-17",
                        List.of(
                                new ImportLot(
                                        "KITCHEN",
                                        100_000,
                                        AllocationMethod.RELATIVE_MRP,
                                        List.of(
                                                new ImportLine("KADAI", "Steel kadai", 12, 10_000,
                                                        null, "BOX-A", null, null),
                                                new ImportLine("TAWA", "Iron tawa", 4, 5_000,
                                                        null, "BOX-A", null, null),
                                                new ImportLine("LADLE", "Ladle", 2, 2_000,
                                                        null, "BOX-B", null, null))))));
        lotId = lots.findAll().get(0).getId();
    }

    private ExpectedLine lineFor(String code) {
        return expectedLines.findAll().stream()
                .filter(l -> l.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private long onHand(String code) {
        return stock.onHand(barcodes.findByCode(code).orElseThrow().getProduct().getId());
    }

    @Test
    @DisplayName("Counting exactly what was expected brings that much on hand")
    void countingInFull() {
        var outcome = counting.countExpected(lineFor("KADAI").getId(), 12, Money.ofPaise(99_900), false, AT);

        assertThat(outcome.quantityCounted()).isEqualTo(12);
        assertThat(outcome.discrepancy()).isZero();
        assertThat(onHand("KADAI")).isEqualTo(12);
    }

    @Test
    @DisplayName("A short count brings on hand only what was found, and the shortfall stays visible")
    void countingShort() {
        var outcome = counting.countExpected(lineFor("KADAI").getId(), 11, null, false, AT);

        assertThat(onHand("KADAI"))
                .as("the ledger holds what someone counted, not what a supplier claimed")
                .isEqualTo(11);
        assertThat(outcome.quantityExpected()).isEqualTo(12);
        assertThat(outcome.discrepancy()).isEqualTo(-1);
        assertThat(lineFor("KADAI").getQuantityOutstanding()).isEqualTo(1);
    }

    @Test
    @DisplayName("A surplus count is recorded rather than refused")
    void countingOver() {
        var outcome = counting.countExpected(lineFor("KADAI").getId(), 13, null, false, AT);

        assertThat(onHand("KADAI")).isEqualTo(13);
        assertThat(outcome.discrepancy())
                .as("more arriving than was promised is a fact, not an error to reject")
                .isEqualTo(1);
        assertThat(lineFor("KADAI").getQuantityOutstanding()).isZero();
    }

    @Test
    @DisplayName("Counting accumulates, so a carton can be left half done and resumed")
    void countingIsResumable() {
        counting.countExpected(lineFor("KADAI").getId(), 5, null, false, AT);

        assertThat(lineFor("KADAI").getQuantityCounted()).isEqualTo(5);
        assertThat(lineFor("KADAI").getQuantityOutstanding()).isEqualTo(7);
        assertThat(onHand("KADAI")).isEqualTo(5);

        // Next morning.
        counting.countExpected(lineFor("KADAI").getId(), 7, null, false, AT.plusSeconds(86_400));

        assertThat(lineFor("KADAI").getQuantityCounted()).isEqualTo(12);
        assertThat(onHand("KADAI"))
                .as("the receipt is written for the increment, not the batch total")
                .isEqualTo(12);
        assertThat(batches.findByLotIdAndProductId(lotId, lineFor("KADAI").getProduct().getId()))
                .as("one batch, however many sittings it took")
                .hasSize(1);
    }

    @Test
    @DisplayName("One product split across cartons accumulates into a single batch")
    void productAcrossCartonsIsOneBatch() {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Sushil"),
                        "2026-07-17",
                        List.of(
                                new ImportLot(
                                        "KITCHEN", 50_000, AllocationMethod.RELATIVE_MRP,
                                        List.of(
                                                new ImportLine("SPLIT", "Split", 2, 10_000, null,
                                                        "BOX-X", null, null),
                                                new ImportLine("SPLIT", "Split", 3, 10_000, null,
                                                        "BOX-Y", null, null))))));

        var lines = expectedLines.findAll().stream()
                .filter(l -> l.getCode().equals("SPLIT")).toList();
        assertThat(lines).hasSize(2);

        counting.countExpected(lines.get(0).getId(), 2, null, false, AT);
        counting.countExpected(lines.get(1).getId(), 3, null, false, AT);

        assertThat(onHand("SPLIT")).isEqualTo(5);
        assertThat(batches.findAll().stream()
                        .filter(b -> b.getProduct().getId().equals(lines.get(0).getProduct().getId()))
                        .count())
                .as("a batch is one product's arrival in one lot, however many cartons")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Goods nobody expected can be recorded, and stay distinguishable")
    void unlistedGoodsAreRecorded() {
        UUID boxId = boxes.findAll().stream()
                .filter(b -> b.getTrackingNumber().equals("BOX-A"))
                .findFirst().orElseThrow().getId();

        var outcome = counting.countUnlisted(
                boxId, "SURPRISE", "Something nobody listed", "KITCHEN", 3,
                Money.ofPaise(49_900), false, AT);

        assertThat(outcome.quantityCounted()).isEqualTo(3);
        assertThat(onHand("SURPRISE")).isEqualTo(3);
        assertThat(expectedLines.findAll().stream()
                        .anyMatch(l -> l.getCode().equals("SURPRISE")))
                .as("it was never expected, so no claim exists for it")
                .isFalse();
    }

    @Test
    @DisplayName("A carton reports where it has got to")
    void boxProgressIsReportable() {
        var before = counting.progressOf(lotId);
        assertThat(before).hasSize(2);
        assertThat(before).allMatch(CartonProgress::notStarted);

        counting.countExpected(lineFor("KADAI").getId(), 5, null, false, AT);

        var mid = counting.progressOf(lotId).stream()
                .filter(b -> b.trackingNumber().equals("BOX-A")).findFirst().orElseThrow();
        assertThat(mid.inProgress())
                .as("a part-counted carton is a normal state, not an exception")
                .isTrue();
        assertThat(mid.unitsExpected()).isEqualTo(16);
        assertThat(mid.unitsCounted()).isEqualTo(5);
        assertThat(mid.finished()).isFalse();
    }

    @Test
    @DisplayName("A carton can be finished short, and the shortfall remains visible")
    void boxFinishesShort() {
        counting.countExpected(lineFor("LADLE").getId(), 1, null, false, AT);
        UUID boxId = lineFor("LADLE").getBox().getId();

        counting.finishBox(boxId, AT);

        var progress = counting.progressOf(lotId).stream()
                .filter(b -> b.boxId().equals(boxId)).findFirst().orElseThrow();
        assertThat(progress.finished()).isTrue();
        assertThat(progress.unitsExpected() - progress.unitsCounted())
                .as("finishing does not paper over what was not there")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Counting against a closed lot is refused")
    void countingAgainstAClosedLotIsRefused() {
        Lot lot = lots.findById(lotId).orElseThrow();
        lot.close(AT);
        lots.save(lot);

        UUID lineId = lineFor("KADAI").getId();
        assertThatThrownBy(() -> counting.countExpected(lineId, 1, null, false, AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }
}
