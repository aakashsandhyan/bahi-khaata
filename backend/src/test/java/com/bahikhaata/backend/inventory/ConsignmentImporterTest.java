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
import com.bahikhaata.contracts.Marketplace;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a manifest becomes once it is recorded.
 *
 * <p>This class had no tests until an import of 3,583 real units was found to have split the
 * cost wrongly while reporting every lot as balancing to the paise. The quantity fault below
 * is the reason it exists.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-consignment-import.db")
@Transactional
class ConsignmentImporterTest {

    @Autowired private ConsignmentImporter importer;
    @Autowired private BatchRepository batches;
    @Autowired private BarcodeRepository barcodes;

    private static ImportLine line(String code, long quantity, long unitValuePaise) {
        return new ImportLine(code, code, quantity, unitValuePaise, null, null, null, null);
    }

    private void imported(List<ImportLine> lines, long paidPaise) {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        "Test supplier",
                        "2026-07-17",
                        List.of(new ImportLot("KITCHEN", paidPaise, AllocationMethod.RELATIVE_MRP, lines))));
    }

    private long unitCostOf(String code) {
        return batches
                .findAll()
                .stream()
                .filter(b -> barcodes.findByCode(code).get().getProduct().getId()
                        .equals(b.getProduct().getId()))
                .findFirst()
                .orElseThrow()
                .getAllocatedUnitCost()
                .paise();
    }

    @Test
    @DisplayName("Quantity decides a line's share once, not twice")
    void quantityIsNotCountedTwice() {
        // Identical goods, differing only in how many arrived. The weighting strategy already
        // multiplies by quantity; handing it a line total multiplies again, so the four-unit
        // line takes four times the share it should and the single unit is squeezed to pay
        // for it. Both lines still sum to what was paid, which is why no total reveals this.
        imported(List.of(line("SINGLE", 1, 10_000), line("FOUR", 4, 10_000)), 50_000);

        assertThat(unitCostOf("FOUR"))
                .as(
                        "four of a thing cost four times one of it, so each unit costs the same;"
                                + " a differing unit cost means quantity was applied twice")
                .isEqualTo(unitCostOf("SINGLE"));
    }

    @Test
    @DisplayName("A uniform factor across the lot reaches every line")
    void aUniformFactorReachesEveryLine() {
        // The real shape of these manifests: pay a fixed fraction of stated value. Paying a
        // quarter must make every line cost a quarter of its own value — not merely make the
        // lot come to a quarter of the whole.
        imported(List.of(line("DEAR", 1, 400_000), line("MANY", 4, 50_000)), 150_000);

        assertThat(unitCostOf("DEAR")).isEqualTo(100_000);
        assertThat(unitCostOf("MANY")).isEqualTo(12_500);
    }

    @Test
    @DisplayName("Rows for one product combine, averaging by value rather than by row")
    void repeatedRowsCombineByValue() {
        // A manifest lists rows, not products, and the same code appears more than once at
        // differing values. Averaging the rows would weight a single unit equally with nine.
        imported(
                List.of(line("SAME", 1, 100_000), line("SAME", 9, 10_000), line("OTHER", 1, 190_000)),
                190_000);

        // Ten units worth 190,000 in total, against one worth 190,000: half the money each.
        assertThat(unitCostOf("SAME")).isEqualTo(9_500);
        assertThat(unitCostOf("OTHER")).isEqualTo(95_000);
    }

    @Test
    @DisplayName("A market price is recorded only where the manifest states one")
    void onlinePriceOnlyWhereStated() {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        "Test supplier",
                        "2026-07-17",
                        List.of(
                                new ImportLot(
                                        "KITCHEN",
                                        20_000,
                                        AllocationMethod.RELATIVE_MRP,
                                        List.of(
                                                new ImportLine(
                                                        "SEEN", "seen", 1, 10_000, null, null,
                                                        129_900L, Marketplace.AMAZON),
                                                new ImportLine(
                                                        "UNSEEN", "unseen", 1, 10_000, null, null,
                                                        null, null))))));

        var seen = barcodes.findByCode("SEEN").orElseThrow().getProduct();
        var unseen = barcodes.findByCode("UNSEEN").orElseThrow().getProduct();

        assertThat(seen.getOnlinePrice().paise()).isEqualTo(129_900);
        assertThat(seen.getOnlinePriceSource()).isEqualTo(Marketplace.AMAZON);
        assertThat(unseen.getOnlinePrice())
                .as("a cost-plus manifest states no market price, and none must be invented")
                .isNull();
    }
}
