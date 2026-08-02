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

import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Deriving a lot's category so a list of same-supplier, same-day lots can be told apart. */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-lot-category.db")
@Transactional
class LotCategoryResolverTest {

    @Autowired private ConsignmentImporter importer;
    @Autowired private LotRepository lots;
    @Autowired private SupplierRepository suppliers;
    @Autowired private LotCategoryResolver resolver;

    private String supplierId(String name) {
        return suppliers.findByNameNormalized(Supplier.normalize(name))
                .map(Supplier::getId)
                .orElseGet(() -> suppliers.save(new Supplier(name, null, null, null, null, null)).getId())
                .toString();
    }

    private UUID importLot(String category, String code) {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Sushil"), "2026-07-17",
                        List.of(new ImportLot(category, 10_000, AllocationMethod.RELATIVE_MRP,
                                List.of(new ImportLine(code, code, 1, 10_000, null, "BOX-" + code, null, null))))));
        return lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b).orElseThrow().getId();
    }

    @Test
    void labelsEachLotWithItsManifestCategory() {
        UUID kitchen = importLot("KITCHEN", "K1");
        UUID footwear = importLot("FOOTWEAR", "F1");

        Map<UUID, String> byLot = resolver.categoryByLot();

        // The two Sushil 2026-07-17 lots are distinguishable by category, which is what the list needs.
        assertThat(byLot).containsEntry(kitchen, "KITCHEN").containsEntry(footwear, "FOOTWEAR");
    }
}
