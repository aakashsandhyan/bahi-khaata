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

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CreateSupplierRequest;
import com.bahikhaata.contracts.ReceiveLotLine;
import com.bahikhaata.contracts.ReceiveLotRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Task 7.2 — the receipt path links a live supplier and refuses anything else. */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-receipt-supplier.db")
@Transactional
class ReceiptSupplierLinkTest {

    @Autowired private GoodsInService goodsIn;
    @Autowired private SupplierService suppliers;
    @Autowired private ProductRepository products;

    private ReceiveLotRequest receiptFrom(String supplierId) {
        Product p = products.save(new Product("Steel bottle", Category.of("KITCHEN"), Map.of()));
        return new ReceiveLotRequest(
                supplierId,
                "2026-07-20",
                10_000_00,
                0,
                List.of(new ReceiveLotLine(p.getId().toString(), 10, 0, 300 * 100, false, null)));
    }

    @Test
    @DisplayName("A received lot links the supplier entity and snapshots its name")
    void linksSupplierAndSnapshotsName() {
        Supplier s = suppliers.create(new CreateSupplierRequest("Liquidator A", null, null, null, null, null));

        GoodsInService.ReceivedLot received = goodsIn.receive(receiptFrom(s.getId().toString()));

        assertThat(received.lot().getSupplierRef().getId()).isEqualTo(s.getId());
        assertThat(received.lot().getSupplier()).isEqualTo("Liquidator A");
    }

    @Test
    @DisplayName("Receiving against an unknown supplier id is rejected")
    void rejectsUnknownSupplier() {
        assertThatThrownBy(() -> goodsIn.receive(receiptFrom(UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no such supplier");
    }

    @Test
    @DisplayName("Receiving against a deactivated supplier is rejected")
    void rejectsInactiveSupplier() {
        Supplier s = suppliers.create(new CreateSupplierRequest("Retired Vendor", null, null, null, null, null));
        suppliers.deactivate(s.getId());
        assertThatThrownBy(() -> goodsIn.receive(receiptFrom(s.getId().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deactivated");
    }
}
