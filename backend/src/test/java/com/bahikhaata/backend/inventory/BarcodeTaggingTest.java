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

import com.bahikhaata.backend.catalog.Barcode;
import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.Origin;
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
 * Tying the goods on the floor to the lines on the sheet.
 *
 * <p>A manifest names goods by a marketplace identifier. Nothing prints that on a pack, so the
 * code a scanner reads and the code the sheet uses never match — found by scanning a real
 * carton. Someone holding the item says which line it is, once, and the mapping is what makes
 * the stock traceable afterwards.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-tagging.db")
@Transactional
class BarcodeTaggingTest {

    private static final Instant AT = Instant.parse("2026-07-21T09:00:00Z");

    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private ExpectedLineRepository expectedLines;
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
                                List.of(
                                        new ImportLine("B07KT9Q54M", "Garbage bags", 3, 10_000,
                                                null, "BOX-A", null, null),
                                        new ImportLine("B0DBHYTM2X", "Earbuds", 2, 20_000,
                                                null, "BOX-A", null, null))))));
        lotId = lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b)
                .orElseThrow().getId();
    }

    private ExpectedLine lineFor(String code) {
        return expectedLines.findByLotIdOrderByCode(lotId).stream()
                .filter(l -> l.getCode().equals(code)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("An imported code is recorded as a marketplace reference, not a barcode")
    void importedCodeIsAMarketplaceReference() {
        assertThat(barcodes.findByCode("B07KT9Q54M").orElseThrow().getOrigin())
                .as("an ASIN is on no pack and no scanner will ever read one off a box")
                .isEqualTo(Origin.MARKETPLACE);
    }

    @Test
    @DisplayName("Tagging records the code on the pack and counts the first unit together")
    void taggingRecordsTheCodeAndCounts() {
        ExpectedLine line = lineFor("B07KT9Q54M");

        var outcome = counting.tagAndCount(
                line.getId(), "8901234567890", 1, Money.ofPaise(24_900), false, AT);

        assertThat(outcome.quantityCounted()).isEqualTo(1);
        Barcode tagged = barcodes.findByCode("8901234567890").orElseThrow();
        assertThat(tagged.getProduct().getId()).isEqualTo(line.getProduct().getId());
        assertThat(tagged.getOrigin())
                .as("this one really is printed on the goods")
                .isEqualTo(Origin.MANUFACTURER);
        assertThat(stock.onHand(line.getProduct().getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Once tagged, the real code resolves on its own and needs no second answer")
    void taggedCodeResolvesAfterwards() {
        ExpectedLine line = lineFor("B07KT9Q54M");
        counting.tagAndCount(line.getId(), "8901234567890", 1, Money.ofPaise(24_900), false, AT);

        // What the screen does on every later scan: resolve the code, then count against the
        // line it already knows.
        var resolved = barcodes.findByCode("8901234567890").orElseThrow().getProduct();
        assertThat(resolved.getId()).isEqualTo(line.getProduct().getId());

        counting.countExpected(line.getId(), 1, null, false, AT);
        assertThat(stock.onHand(line.getProduct().getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("A product ends up holding both codes, and both find it")
    void bothCodesFindTheProduct() {
        ExpectedLine line = lineFor("B07KT9Q54M");
        counting.tagAndCount(line.getId(), "8901234567890", 1, Money.ofPaise(24_900), false, AT);

        UUID productId = line.getProduct().getId();
        assertThat(barcodes.findByCode("B07KT9Q54M").orElseThrow().getProduct().getId())
                .as("the sheet's reference still matches the manifest")
                .isEqualTo(productId);
        assertThat(barcodes.findByCode("8901234567890").orElseThrow().getProduct().getId())
                .as("and the code on the pack now finds the same thing")
                .isEqualTo(productId);
    }

    @Test
    @DisplayName("A code already meaning something else is refused, naming what it means")
    void aCodeCannotMeanTwoThings() {
        counting.tagAndCount(lineFor("B07KT9Q54M").getId(), "8901234567890", 1, null, false, AT);
        UUID earbuds = lineFor("B0DBHYTM2X").getId();

        assertThatThrownBy(
                        () -> counting.tagAndCount(earbuds, "8901234567890", 1, null, false, AT))
                .as("a code resolving to two products resolves to neither")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Garbage bags");
    }

    @Test
    @DisplayName("Tagging the same code to the same line again is harmless")
    void taggingTwiceIsHarmless() {
        ExpectedLine line = lineFor("B07KT9Q54M");
        counting.tagAndCount(line.getId(), "8901234567890", 1, Money.ofPaise(24_900), false, AT);
        counting.tagAndCount(line.getId(), "8901234567890", 1, null, false, AT);

        assertThat(stock.onHand(line.getProduct().getId())).isEqualTo(2);
        assertThat(barcodes.findAll().stream()
                        .filter(b -> b.getCode().equals("8901234567890")).count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Tagging against a closed delivery is refused")
    void taggingAgainstAClosedLotIsRefused() {
        Lot lot = lots.findById(lotId).orElseThrow();
        lot.close(AT);
        lots.save(lot);
        UUID lineId = lineFor("B07KT9Q54M").getId();

        assertThatThrownBy(() -> counting.tagAndCount(lineId, "8901234567890", 1, null, false, AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }
}
