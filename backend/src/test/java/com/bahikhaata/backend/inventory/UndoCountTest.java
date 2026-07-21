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
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.MovementType;
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
 * Taking back a count that should not have happened.
 *
 * <p>A wrong scan is noticed within seconds: the name on screen is not the thing in your hand.
 * Before this there was no way back — the count stood, the code mapping stood, and the only
 * remedy was remembering the mistake, which is not a remedy.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-undo.db")
@Transactional
class UndoCountTest {

    private static final Instant AT = Instant.parse("2026-07-21T09:00:00Z");

    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private LotClosing closing;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BatchRepository batches;
    @Autowired private BarcodeRepository barcodes;
    @Autowired private StockLedgerRepository ledger;
    @Autowired private LotRepository lots;
    @Autowired private StockLevels stock;

    private UUID lotId;

    @BeforeEach
    void importAManifest() {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        "Sushil", "2026-07-17",
                        List.of(new ImportLot("KITCHEN", 100_000, AllocationMethod.RELATIVE_MRP,
                                List.of(
                                        new ImportLine("RIGHT", "Right thing", 3, 10_000, null,
                                                "BOX-A", null, null),
                                        new ImportLine("WRONG", "Wrong thing", 3, 10_000, null,
                                                "BOX-A", null, null))))));
        lotId = lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b)
                .orElseThrow().getId();
    }

    private ExpectedLine line(String code) {
        return expectedLines.findByLotIdOrderByCode(lotId).stream()
                .filter(l -> l.getCode().equals(code)).findFirst().orElseThrow();
    }

    private long onHand(String code) {
        return stock.onHand(barcodes.findByCode(code).orElseThrow().getProduct().getId());
    }

    @Test
    @DisplayName("A mistaken count comes back off, stock and all")
    void countIsTakenBack() {
        counting.countExpected(line("WRONG").getId(), StockCondition.GOOD, 1, null, false, AT);
        assertThat(onHand("WRONG")).isEqualTo(1);

        counting.undoCount(line("WRONG").getId(), StockCondition.GOOD, 1, null, AT);

        assertThat(onHand("WRONG")).isZero();
        assertThat(line("WRONG").getQuantityCounted()).isZero();
    }

    @Test
    @DisplayName("The receipt stays; the correction is a second entry")
    void theLedgerKeepsWhatHappened() {
        counting.countExpected(line("WRONG").getId(), StockCondition.GOOD, 1, null, false, AT);
        counting.undoCount(line("WRONG").getId(), StockCondition.GOOD, 1, null, AT);

        List<StockLedgerEntry> entries = ledger.findAll();
        assertThat(entries)
                .as("an append-only ledger corrects by adding, never by erasing")
                .anyMatch(e -> e.getMovementType() == MovementType.PURCHASE_RECEIPT)
                .anyMatch(e -> e.getMovementType() == MovementType.ADJUSTMENT);
        assertThat(entries.stream()
                        .filter(e -> e.getMovementType() == MovementType.ADJUSTMENT)
                        .findFirst().orElseThrow().getQuantity())
                .isEqualTo(-1);
    }

    @Test
    @DisplayName("Undoing a scan that taught a code forgets the code as well")
    void aWrongMappingCeasesToExist() {
        // The mistake that matters: a sticker tagged to the wrong goods keeps resolving them,
        // confidently, forever.
        counting.tagAndCount(line("WRONG").getId(), "LPNBLR5Q0001111", StockCondition.GOOD, 1,
                null, false, AT);
        assertThat(barcodes.findByCode("LPNBLR5Q0001111")).isPresent();

        counting.undoCount(line("WRONG").getId(), StockCondition.GOOD, 1, "LPNBLR5Q0001111", AT);

        assertThat(barcodes.findByCode("LPNBLR5Q0001111"))
                .as("so the sticker can be scanned again and sent to the right item")
                .isEmpty();
    }

    @Test
    @DisplayName("The supplier's own reference is never unmapped")
    void theManifestReferenceSurvives() {
        counting.countExpected(line("WRONG").getId(), StockCondition.GOOD, 1, null, false, AT);

        counting.undoCount(line("WRONG").getId(), StockCondition.GOOD, 1, "WRONG", AT);

        assertThat(barcodes.findByCode("WRONG"))
                .as("that came from the manifest and is not ours to remove")
                .isPresent();
    }

    @Test
    @DisplayName("A delivery still costs correctly after a count was taken back")
    void closingIgnoresWhatWasTakenBack() {
        counting.countExpected(line("RIGHT").getId(), StockCondition.GOOD, 2, null, false, AT);
        counting.countExpected(line("WRONG").getId(), StockCondition.GOOD, 1, null, false, AT);
        counting.undoCount(line("WRONG").getId(), StockCondition.GOOD, 1, null, AT);

        closing.close(lotId, true, AT);

        UUID rightProduct = barcodes.findByCode("RIGHT").orElseThrow().getProduct().getId();
        assertThat(batches.findByLotIdAndProductIdAndCondition(
                        lotId, rightProduct, StockCondition.GOOD).orElseThrow()
                        .getAllocatedTotal())
                .as("a batch corrected back to nothing carries no share, and cannot be asked to")
                .isEqualTo(Money.ofPaise(100_000));
    }

    @Test
    @DisplayName("A count can be taken back without saying what condition it was")
    void conditionNeedNotBeNamed() {
        // What a row on screen knows is "three counted", not how those three were split. It
        // should not have to guess in order to correct one.
        counting.countExpected(line("WRONG").getId(), StockCondition.DAMAGED, 2, null, false, AT);

        counting.undoCount(line("WRONG").getId(), null, 1, null, AT);

        assertThat(line("WRONG").getQuantityCounted()).isEqualTo(1);
        assertThat(onHand("WRONG")).isEqualTo(1);
    }

    @Test
    @DisplayName("Taking back an item counted long before the last scan works the same")
    void anyEarlierCountCanBeTakenBack() {
        counting.countExpected(line("WRONG").getId(), StockCondition.GOOD, 1, null, false, AT);
        // Ten more items go by before anyone notices.
        counting.countExpected(line("RIGHT").getId(), StockCondition.GOOD, 3, null, false, AT);

        counting.undoCount(line("WRONG").getId(), null, 1, null, AT);

        assertThat(onHand("WRONG")).isZero();
        assertThat(onHand("RIGHT"))
                .as("correcting one line must leave the others alone")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("Taking back from an item nothing was counted for is refused")
    void nothingToTakeBack() {
        assertThatThrownBy(() -> counting.undoCount(line("WRONG").getId(), null, 1, null, AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nothing is counted");
    }

    @Test
    @DisplayName("Taking back more than was counted is refused")
    void cannotUndoMoreThanHappened() {
        counting.countExpected(line("WRONG").getId(), StockCondition.GOOD, 1, null, false, AT);

        assertThatThrownBy(
                        () -> counting.undoCount(
                                line("WRONG").getId(), StockCondition.GOOD, 5, null, AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot take off 5");
    }

    @Test
    @DisplayName("Nothing can be taken back once the delivery is closed")
    void closedDeliveriesAreFinal() {
        counting.countExpected(line("RIGHT").getId(), StockCondition.GOOD, 1, null, false, AT);
        closing.close(lotId, true, AT);
        UUID lineId = line("RIGHT").getId();

        assertThatThrownBy(
                        () -> counting.undoCount(lineId, StockCondition.GOOD, 1, null, AT))
                .as("its costs may already have set prices")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }
}
