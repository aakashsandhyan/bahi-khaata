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
                        supplierId("Sushil"), "2026-07-17",
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
                line.getId(), "8901234567890", StockCondition.GOOD, 1,
                Money.ofPaise(24_900), false, AT);

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
        counting.tagAndCount(line.getId(), "8901234567890", StockCondition.GOOD, 1,
                Money.ofPaise(24_900), false, AT);

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
        counting.tagAndCount(line.getId(), "8901234567890", StockCondition.GOOD, 1,
                Money.ofPaise(24_900), false, AT);

        UUID productId = line.getProduct().getId();
        assertThat(barcodes.findByCode("B07KT9Q54M").orElseThrow().getProduct().getId())
                .as("the sheet's reference still matches the manifest")
                .isEqualTo(productId);
        assertThat(barcodes.findByCode("8901234567890").orElseThrow().getProduct().getId())
                .as("and the code on the pack now finds the same thing")
                .isEqualTo(productId);
    }

    @Test
    @DisplayName("A manufacturer code already on a sibling row counts here without stealing the code")
    void aManufacturerCodeOnASiblingRowCountsWithoutRetagging() {
        counting.tagAndCount(lineFor("B07KT9Q54M").getId(), "8901234567890",
                StockCondition.GOOD, 1, null, false, AT);
        ExpectedLine earbuds = lineFor("B0DBHYTM2X");

        // The same printed barcode turning up on another marketplace listing of the same physical
        // item is routine, not an error. It counts against the line in hand; the code is not moved.
        counting.tagAndCount(earbuds.getId(), "8901234567890", StockCondition.GOOD, 1, null,
                false, AT);

        assertThat(stock.onHand(earbuds.getProduct().getId()))
                .as("the unit is counted where the operator pointed")
                .isEqualTo(1);
        assertThat(barcodes.findByCode("8901234567890").orElseThrow().getProduct().getId())
                .as("the code stays on its first owner; duplicates collapse at pricing, not here")
                .isEqualTo(lineFor("B07KT9Q54M").getProduct().getId());
    }

    @Test
    @DisplayName("A returns label already on another product is refused — one sticker, one item")
    void aReturnsLabelCannotMeanTwoThings() {
        counting.tagAndCount(lineFor("B07KT9Q54M").getId(), "LPNBLR5Q0000042",
                StockCondition.GOOD, 1, null, false, AT);
        UUID earbuds = lineFor("B0DBHYTM2X").getId();

        assertThatThrownBy(
                        () -> counting.tagAndCount(
                                earbuds, "LPNBLR5Q0000042", StockCondition.GOOD, 1, null, false,
                                AT))
                .as("a returns label names one physical unit and cannot be on two products")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Garbage bags");
    }

    @Test
    @DisplayName("Tagging the same code to the same line again is harmless")
    void taggingTwiceIsHarmless() {
        ExpectedLine line = lineFor("B07KT9Q54M");
        counting.tagAndCount(line.getId(), "8901234567890", StockCondition.GOOD, 1,
                Money.ofPaise(24_900), false, AT);
        counting.tagAndCount(line.getId(), "8901234567890", StockCondition.GOOD, 1, null,
                false, AT);

        assertThat(stock.onHand(line.getProduct().getId())).isEqualTo(2);
        assertThat(barcodes.findAll().stream()
                        .filter(b -> b.getCode().equals("8901234567890")).count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("A returns sticker is recorded as a unit label, not a manufacturer code")
    void aReturnsStickerIsAUnitLabel() {
        ExpectedLine line = lineFor("B07KT9Q54M");

        counting.tagAndCount(line.getId(), "LPNBLR5Q0994129", StockCondition.GOOD, 1, null,
                false, AT);

        assertThat(barcodes.findByCode("LPNBLR5Q0994129").orElseThrow().getOrigin())
                .as("it names one physical item and will never be seen again, so it is not the"
                        + " product's barcode however well it resolves today")
                .isEqualTo(Origin.UNIT_LABEL);
    }

    @Test
    @DisplayName("Many unit labels may point at one product")
    void manyUnitLabelsOneProduct() {
        // The sticker covers the printed barcode, so every unit of a product scans as something
        // new. That is workable only because a product was never limited to one code.
        ExpectedLine line = lineFor("B07KT9Q54M");
        counting.tagAndCount(line.getId(), "LPNBLR5Q0000001", StockCondition.GOOD, 1, null, false, AT);
        counting.tagAndCount(line.getId(), "LPNBLR5Q0000002", StockCondition.GOOD, 1, null, false, AT);
        counting.tagAndCount(line.getId(), "LPNBLR5Q0000003", StockCondition.GOOD, 1, null, false, AT);

        UUID productId = line.getProduct().getId();
        assertThat(barcodes.findByCode("LPNBLR5Q0000001").orElseThrow().getProduct().getId())
                .isEqualTo(productId);
        assertThat(barcodes.findByCode("LPNBLR5Q0000003").orElseThrow().getProduct().getId())
                .as("any of them resolves the same product, which is what checkout needs")
                .isEqualTo(productId);
        assertThat(stock.onHand(productId)).isEqualTo(3);
    }

    @Test
    @DisplayName("A printed barcode is still recorded as the manufacturer's")
    void aPrintedBarcodeIsStillTheManufacturers() {
        counting.tagAndCount(lineFor("B07KT9Q54M").getId(), "8901234567890", StockCondition.GOOD,
                1, null, false, AT);

        assertThat(barcodes.findByCode("8901234567890").orElseThrow().getOrigin())
                .as("an EAN names the product and will appear on every future delivery of it")
                .isEqualTo(Origin.MANUFACTURER);
    }

    @Test
    @DisplayName("Any code a product has gathered finds its line again")
    void anyKnownCodeResolves() {
        ExpectedLine line = lineFor("B07KT9Q54M");
        UUID boxId = line.getBox().getId();
        counting.tagAndCount(line.getId(), "LPNBLR5Q0000001", StockCondition.GOOD, 1, null,
                false, AT);
        counting.tagAndCount(line.getId(), "8901234567890", StockCondition.GOOD, 1, null,
                false, AT);

        // The manifest's own reference, a returns sticker, and the maker's printed barcode all
        // name the same goods. Any of them in someone's hand must find the line without them
        // being asked which item it is.
        assertThat(counting.resolveInBox(boxId, "B07KT9Q54M"))
                .as("the supplier's reference")
                .isPresent();
        assertThat(counting.resolveInBox(boxId, "LPNBLR5Q0000001"))
                .as("a returns sticker tagged earlier")
                .isPresent();
        assertThat(counting.resolveInBox(boxId, "8901234567890"))
                .as("the printed barcode")
                .isPresent();
        assertThat(counting.resolveInBox(boxId, "B07KT9Q54M").orElseThrow().lineId())
                .isEqualTo(line.getId());
    }

    @Test
    @DisplayName("A code from another carton does not resolve in this one")
    void codesDoNotLeakBetweenCartons() {
        ExpectedLine line = lineFor("B07KT9Q54M");
        counting.tagAndCount(line.getId(), "LPNBLR5Q0000009", StockCondition.GOOD, 1, null,
                false, AT);

        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Sushil"), "2026-07-17",
                        List.of(new ImportLot("KITCHEN", 10_000, AllocationMethod.RELATIVE_MRP,
                                List.of(new ImportLine("OTHER", "Other", 1, 1_000, null,
                                        "BOX-ELSEWHERE", null, null))))));
        UUID elsewhere = expectedLines.findAll().stream()
                .filter(l -> l.getCode().equals("OTHER")).findFirst().orElseThrow()
                .getBox().getId();

        assertThat(counting.resolveInBox(elsewhere, "LPNBLR5Q0000009"))
                .as("the code is known, but not as something expected in this carton")
                .isEmpty();
    }

    @Test
    @DisplayName("An unknown code resolves to nothing rather than failing")
    void unknownCodeIsOrdinary() {
        assertThat(counting.resolveInBox(lineFor("B07KT9Q54M").getBox().getId(), "NEVERSEEN"))
                .as("a sticker nobody has seen is the ordinary case, not an error")
                .isEmpty();
    }

    @Test
    @DisplayName("Tagging against a closed delivery is refused")
    void taggingAgainstAClosedLotIsRefused() {
        Lot lot = lots.findById(lotId).orElseThrow();
        lot.close(AT);
        lots.save(lot);
        UUID lineId = lineFor("B07KT9Q54M").getId();

        assertThatThrownBy(() -> counting.tagAndCount(
                                lineId, "8901234567890", StockCondition.GOOD, 1, null, false, AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }
}
