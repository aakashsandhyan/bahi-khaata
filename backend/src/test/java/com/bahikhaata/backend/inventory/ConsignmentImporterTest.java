/*
 * bahi-khaata — point of sale for Bachat Bazaar
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
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.ImportResult;
import com.bahikhaata.contracts.LotState;
import com.bahikhaata.contracts.Marketplace;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a manifest becomes once it is recorded: an expectation, and nothing on hand.
 *
 * <p>This class had no tests until an import of 3,583 real units was found to have split the
 * cost wrongly while reporting every lot as balancing to the paise — and, separately, to have
 * put all of it on hand without a box being opened. Both faults are pinned here.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-consignment-import.db")
@Transactional
class ConsignmentImporterTest {

    @Autowired private ConsignmentImporter importer;
    @Autowired private BatchRepository batches;
    @Autowired private BarcodeRepository barcodes;
    @Autowired private ProductRepository products;
    @Autowired private BoxRepository boxes;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private StockLedgerRepository ledger;
    @Autowired private StockLevels stock;
    @Autowired private SupplierRepository suppliers;

    private String supplierId(String name) {
        return suppliers.findByNameNormalized(Supplier.normalize(name))
                .map(Supplier::getId)
                .orElseGet(() -> suppliers.save(new Supplier(name, null, null, null, null, null)).getId())
                .toString();
    }

    private static ImportLine line(String box, String code, long quantity, long unitValuePaise) {
        return new ImportLine(code, code, quantity, unitValuePaise, null, box, null, null);
    }

    private ImportResult imported(List<ImportLine> lines, long paidPaise) {
        return importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Test supplier"),
                        "2026-07-17",
                        List.of(
                                new ImportLot(
                                        "KITCHEN", paidPaise, AllocationMethod.RELATIVE_MRP,
                                        lines))));
    }

    @Test
    @DisplayName("Importing a manifest puts nothing on hand")
    void importPutsNothingOnHand() {
        long ledgerBefore = ledger.count();

        ImportResult result =
                imported(List.of(line("BOX-1", "AAA", 12, 10_000), line("BOX-2", "BBB", 3, 20_000)), 50_000);

        assertThat(result.unitsExpected()).isEqualTo(15);
        assertThat(ledger.count())
                .as("a manifest is a claim; recording it as stock states quantities nobody counted")
                .isEqualTo(ledgerBefore);
        assertThat(batches.count()).isZero();
        assertThat(stock.onHand(barcodes.findByCode("AAA").orElseThrow().getProduct().getId()))
                .isZero();
    }

    @Test
    @DisplayName("A lot opens on import and is costed only when it closes")
    void importedLotIsOpenAndUncosted() {
        imported(List.of(line("BOX-1", "AAA", 2, 10_000)), 20_000);

        var line = expectedLines.findAll().get(0);
        assertThat(line.getLot().getState()).isEqualTo(LotState.OPEN);
        assertThat(line.getLot().getClosedAt()).isNull();
    }

    @Test
    @DisplayName("A named product becomes a catalogue entry holding no stock")
    void productIsCreatedWithoutStock() {
        imported(List.of(line("BOX-1", "NEWCODE", 5, 10_000)), 10_000);

        var product = barcodes.findByCode("NEWCODE").orElseThrow().getProduct();
        assertThat(products.findById(product.getId())).isPresent();
        assertThat(stock.onHand(product.getId())).isZero();
    }

    @Test
    @DisplayName("A known code is matched, not duplicated")
    void knownCodeIsMatched() {
        imported(List.of(line("BOX-1", "SHARED", 1, 10_000)), 10_000);
        long productsAfterFirst = products.count();

        ImportResult second = imported(List.of(line("BOX-9", "SHARED", 1, 10_000)), 10_000);

        assertThat(second.productsMatched()).isEqualTo(1);
        assertThat(second.productsCreated()).isZero();
        assertThat(products.count()).isEqualTo(productsAfterFirst);
        assertThat(barcodes.findAll().stream().filter(b -> b.getCode().equals("SHARED")).count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("One product split across cartons is one line per carton")
    void productAcrossCartonsIsOneLinePerCarton() {
        // 449 products in the first real consignment arrived in more than one carton, one of
        // them across 24. Each carton is opened separately, so each needs its own line.
        ImportResult result =
                imported(
                        List.of(
                                line("BOX-1", "SPLIT", 2, 10_000),
                                line("BOX-2", "SPLIT", 3, 10_000)),
                        50_000);

        assertThat(result.expectedLinesCreated()).isEqualTo(2);
        assertThat(result.boxesCreated()).isEqualTo(2);
        assertThat(result.productsCreated()).as("still one product").isEqualTo(1);
        assertThat(result.unitsExpected()).isEqualTo(5);
    }

    @Test
    @DisplayName("Repeated rows for one product in one carton combine into a single line")
    void repeatedRowsInOneCartonCombine() {
        ImportResult result =
                imported(
                        List.of(
                                line("BOX-1", "SAME", 1, 10_000),
                                line("BOX-1", "SAME", 9, 10_000)),
                        50_000);

        assertThat(result.expectedLinesCreated())
                .as("one thing to find when that box is opened, not two")
                .isEqualTo(1);
        assertThat(expectedLines.findAll().get(0).getQuantityExpected()).isEqualTo(10);
    }

    @Test
    @DisplayName("The stated value is kept per unit, so quantity is never applied twice")
    void statedValueIsPerUnit() {
        imported(List.of(line("BOX-1", "WEIGHED", 4, 10_000)), 40_000);

        assertThat(expectedLines.findAll().get(0).getStatedValue().paise())
                .as("a line total here would be multiplied by quantity again at allocation")
                .isEqualTo(10_000);
    }

    @Test
    @DisplayName("A line stating no value at all is recorded without one")
    void lineWithNoStatedValue() {
        imported(List.of(line("BOX-1", "NOVALUE", 1, 0)), 10_000);

        assertThat(expectedLines.findAll().get(0).getStatedValue())
                .as("weighed at the lot average when the lot is closed")
                .isNull();
    }

    @Test
    @DisplayName("An unknown category fails the import and records nothing")
    void unknownCategoryFailsAndRecordsNothing() {
        long lotsBefore = boxes.count();

        assertThatThrownBy(
                        () ->
                                importer.importConsignment(
                                        new ImportConsignmentRequest(
                                                supplierId("Test supplier"),
                                                "2026-07-17",
                                                List.of(
                                                        new ImportLot(
                                                                "NO_SUCH_CATEGORY", 10_000,
                                                                AllocationMethod.RELATIVE_MRP,
                                                                List.of(line("BOX-1", "X", 1, 100)))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NO_SUCH_CATEGORY");

        assertThat(boxes.count()).isEqualTo(lotsBefore);
    }

    @Test
    @DisplayName("A line without a carton is refused")
    void lineWithoutCartonIsRefused() {
        assertThatThrownBy(() -> new ImportLine("X", "x", 1, 100, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carton");
    }

    @Test
    @DisplayName("Repeated rows average their online price by quantity, not by row order")
    void onlinePriceIsAveragedNotTakenFromTheLastRow() {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Test supplier"),
                        "2026-07-17",
                        List.of(
                                new ImportLot(
                                        "KITCHEN",
                                        20_000,
                                        AllocationMethod.RELATIVE_MRP,
                                        List.of(
                                                new ImportLine(
                                                        "VARIED", "varied", 1, 10_000, null,
                                                        "BOX-1", 100_000L, Marketplace.AMAZON),
                                                new ImportLine(
                                                        "VARIED", "varied", 3, 10_000, null,
                                                        "BOX-1", 200_000L, Marketplace.AMAZON))))));

        // Three units at 200,000 and one at 100,000: 175,000, not the 200,000 of the last row
        // nor the 150,000 a plain average of the two rows would give.
        assertThat(barcodes.findByCode("VARIED").orElseThrow().getProduct().getOnlinePrice().paise())
                .isEqualTo(175_000);
    }

    @Test
    @DisplayName("Prices from two marketplaces are refused rather than averaged together")
    void mixedMarketplacesAreRefused() {
        assertThatThrownBy(
                        () ->
                                importer.importConsignment(
                                        new ImportConsignmentRequest(
                                                supplierId("Test supplier"),
                                                "2026-07-17",
                                                List.of(
                                                        new ImportLot(
                                                                "KITCHEN", 20_000,
                                                                AllocationMethod.RELATIVE_MRP,
                                                                List.of(
                                                                        new ImportLine(
                                                                                "MIXED", "m", 1,
                                                                                10_000, null,
                                                                                "BOX-1", 100_000L,
                                                                                Marketplace.AMAZON),
                                                                        new ImportLine(
                                                                                "MIXED", "m", 1,
                                                                                10_000, null,
                                                                                "BOX-1", 200_000L,
                                                                                Marketplace
                                                                                        .FLIPKART)))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two marketplaces");
    }

    @Test
    @DisplayName("A cost-plus manifest records no online price")
    void costPlusRecordsNoOnlinePrice() {
        imported(List.of(line("BOX-1", "COSTONLY", 1, 10_000)), 10_000);

        assertThat(barcodes.findByCode("COSTONLY").orElseThrow().getProduct().getOnlinePrice())
                .as("a supplier's cost filed as an Amazon price would be the same conflation")
                .isNull();
    }
}
