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

import com.bahikhaata.backend.catalog.Barcode;
import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.CatalogDetail;
import com.bahikhaata.contracts.CatalogEntry;
import com.bahikhaata.contracts.CatalogStatus;
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
 * The catalogue splits products into found and on-paper, derived from batches and codes, and lets a
 * person browse either by name — the goods still unfound surfaced first.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-catalog.db")
@Transactional
class ProductCatalogTest {

    private static final Instant AT = Instant.parse("2026-07-25T09:00:00Z");

    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private ProductCatalog catalog;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BarcodeRepository barcodes;
    @Autowired private LotRepository lots;

    private UUID lotId;

    @BeforeEach
    void importThreeProducts() {
        // Codes AAA/BBB/CCC so findByLotIdOrderByCode returns them in a known order.
        importer.importConsignment(
                new ImportConsignmentRequest(
                        "Sushil", "2026-07-17",
                        List.of(new ImportLot("KITCHEN", 100_000, AllocationMethod.RELATIVE_MRP,
                                List.of(
                                        new ImportLine("AAA", "Alpha Kettle", 4, 10_000, null, "BOX-A", null, null),
                                        new ImportLine("BBB", "Beta Cooker", 4, 10_000, null, "BOX-A", null, null),
                                        new ImportLine("CCC", "Gamma Pan", 4, 10_000, null, "BOX-A", null, null))))));
        lotId = lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b).orElseThrow().getId();
    }

    private ExpectedLine lineAt(int i) {
        return expectedLines.findByLotIdOrderByCode(lotId).get(i);
    }

    private List<String> names(List<CatalogEntry> entries) {
        return entries.stream().map(CatalogEntry::name).toList();
    }

    @Test
    @DisplayName("A freshly imported product, never found, is on-paper")
    void importedIsOnPaper() {
        List<CatalogEntry> onPaper = catalog.browse("", "on-paper", 0, 25);
        assertThat(names(onPaper))
                .containsExactlyInAnyOrder("Alpha Kettle", "Beta Cooker", "Gamma Pan");
        assertThat(onPaper).allMatch(e -> e.status() == CatalogStatus.ON_PAPER);
        assertThat(catalog.browse("", "found", 0, 25)).isEmpty();
    }

    @Test
    @DisplayName("A counted product becomes found, even with no physical code")
    void countedIsFound() {
        counting.countExpected(lineAt(0).getId(), StockCondition.GOOD, 2, Money.ofPaise(20_000), false, AT);

        assertThat(names(catalog.browse("", "found", 0, 25))).containsExactly("Alpha Kettle");
        assertThat(names(catalog.browse("", "on-paper", 0, 25)))
                .containsExactlyInAnyOrder("Beta Cooker", "Gamma Pan");
    }

    @Test
    @DisplayName("A physical code makes a product found even before it is counted")
    void taggedIsFound() {
        Product beta = lineAt(1).getProduct();
        barcodes.save(new Barcode(beta, "EAN-BETA", Origin.MANUFACTURER));

        assertThat(names(catalog.browse("", "found", 0, 25))).containsExactly("Beta Cooker");
    }

    @Test
    @DisplayName("A marketplace reference alone does not count as found")
    void marketplaceCodeIsNotFound() {
        // Every product already carries its import MARKETPLACE code; none should read as found.
        assertThat(catalog.browse("", "found", 0, 25)).isEmpty();
    }

    @Test
    @DisplayName("Name filter matches a fragment, case-insensitively")
    void nameFilter() {
        assertThat(names(catalog.browse("kettle", "all", 0, 25))).containsExactly("Alpha Kettle");
        assertThat(names(catalog.browse("BETA", "all", 0, 25))).containsExactly("Beta Cooker");
    }

    @Test
    @DisplayName("The all view marks each row found or on-paper")
    void allMarksMixed() {
        counting.countExpected(lineAt(0).getId(), StockCondition.GOOD, 2, Money.ofPaise(20_000), false, AT);

        List<CatalogEntry> all = catalog.browse("", "all", 0, 25);
        assertThat(all).hasSize(3);
        assertThat(all.stream().filter(e -> e.status() == CatalogStatus.FOUND).map(CatalogEntry::name))
                .containsExactly("Alpha Kettle");
        assertThat(all.stream().filter(e -> e.status() == CatalogStatus.ON_PAPER).map(CatalogEntry::name))
                .containsExactlyInAnyOrder("Beta Cooker", "Gamma Pan");
    }

    @Test
    @DisplayName("Paging returns further products beyond the first page")
    void paging() {
        List<CatalogEntry> page0 = catalog.browse("", "all", 0, 2);
        List<CatalogEntry> page1 = catalog.browse("", "all", 1, 2);
        assertThat(page0).hasSize(2);
        assertThat(page1).hasSize(1);
        assertThat(names(page0)).doesNotContainAnyElementsOf(names(page1));
        // Ordered by name across pages: Alpha, Beta, then Gamma.
        assertThat(names(page0)).containsExactly("Alpha Kettle", "Beta Cooker");
        assertThat(names(page1)).containsExactly("Gamma Pan");
    }

    @Test
    @DisplayName("Detail returns states, codes, and standing; reuses the states view")
    void detail() {
        Product alpha = lineAt(0).getProduct();
        counting.countExpected(lineAt(0).getId(), StockCondition.GOOD, 2, Money.ofPaise(20_000), false, AT);

        CatalogDetail d = catalog.detail(alpha.getId());
        assertThat(d.status()).isEqualTo(CatalogStatus.FOUND);
        assertThat(d.priced()).isFalse();
        assertThat(d.states().productName()).isEqualTo("Alpha Kettle");
        assertThat(d.states().lines()).isNotEmpty();
        assertThat(d.codes()).anyMatch(c -> c.origin() == Origin.MARKETPLACE && c.code().equals("AAA"));
    }
}
