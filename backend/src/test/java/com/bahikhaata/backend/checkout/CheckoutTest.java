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
package com.bahikhaata.backend.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.inventory.ConsignmentImporter;
import com.bahikhaata.backend.inventory.ExpectedLine;
import com.bahikhaata.backend.inventory.ExpectedLineRepository;
import com.bahikhaata.backend.inventory.GoodsInCounting;
import com.bahikhaata.backend.inventory.Lot;
import com.bahikhaata.backend.inventory.LotRepository;
import com.bahikhaata.backend.inventory.Supplier;
import com.bahikhaata.backend.inventory.SupplierRepository;
import com.bahikhaata.backend.shelf.ProductPricing;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.CartView;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Ringing up a sale, and the saving it shows. */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-checkout.db")
@Transactional
class CheckoutTest {

    private static final Instant AT = Instant.parse("2026-07-23T09:00:00Z");

    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private ProductPricing pricing;
    @Autowired private Checkout checkout;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BarcodeRepository barcodes;
    @Autowired private LotRepository lots;
    @Autowired private SupplierRepository suppliers;

    private String supplierId(String name) {
        return suppliers.findByNameNormalized(Supplier.normalize(name))
                .map(Supplier::getId)
                .orElseGet(() -> suppliers.save(new Supplier(name, null, null, null, null, null)).getId())
                .toString();
    }

    /**
     * Brings one product to the shelf the way pricing does: received, counted with an MRP, costed,
     * and priced. Deliberately does NOT print a label — a product is sellable once priced with a
     * confirmed MRP, and the till must not depend on the label having printed. Returns its code.
     */
    private String onTheShelf(String code, long onlinePaise, long mrpPaise, long pricePaise) {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Sushil"), "2026-07-17",
                        List.of(new ImportLot("HOME_ESSENTIALS", onlinePaise / 4,
                                AllocationMethod.RELATIVE_MRP,
                                List.of(new ImportLine(code, code, 5, onlinePaise, null,
                                        "BOX-" + code, null, null))))));
        UUID lotId = lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b)
                .orElseThrow().getId();
        ExpectedLine line = expectedLines.findByLotIdOrderByCode(lotId).get(0);
        counting.countExpected(line.getId(), StockCondition.GOOD, 5, Money.ofPaise(mrpPaise),
                false, AT);
        // Costed at receipt from the manifest line; the lot need not be closed to price or label.
        UUID productId = line.getProduct().getId();
        pricing.setSellingPrice(productId, Money.ofPaise(pricePaise));
        return code;
    }

    @Test
    @DisplayName("A scan adds the item at its price, with the saving against MRP")
    void scanShowsTheSaving() {
        String code = onTheShelf("KADAI", 40_000, 99_900, 49_900);

        CartView cart = checkout.open();
        cart = checkout.scan(cart.cartId(), code);

        assertThat(cart.lines()).hasSize(1);
        var line = cart.lines().get(0);
        assertThat(line.mrpPaise()).isEqualTo(99_900);
        assertThat(line.unitPricePaise()).isEqualTo(49_900);
        assertThat(line.savingPaise()).as("MRP minus price").isEqualTo(50_000);
        assertThat(line.savingPercent()).isEqualTo(50);
        assertThat(cart.savingPaise()).isEqualTo(50_000);
        assertThat(cart.subtotalPaise()).isEqualTo(49_900);
    }

    @Test
    @DisplayName("Scanning the same thing twice raises the quantity, not a second line")
    void secondScanRaisesQuantity() {
        String code = onTheShelf("MUG", 20_000, 40_000, 15_000);

        CartView cart = checkout.open();
        checkout.scan(cart.cartId(), code);
        cart = checkout.scan(cart.cartId(), code);

        assertThat(cart.lines()).hasSize(1);
        assertThat(cart.lines().get(0).quantity()).isEqualTo(2);
        assertThat(cart.lines().get(0).lineTotalPaise()).isEqualTo(30_000);
        assertThat(cart.savingPaise()).as("25,000 saved on each of two").isEqualTo(50_000);
    }

    @Test
    @DisplayName("Goods with no price cannot be sold, and the till says so")
    void unpricedIsRefused() {
        // Received and labelled would still need a price; here it never gets one.
        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Sushil"), "2026-07-17",
                        List.of(new ImportLot("HOME_ESSENTIALS", 10_000,
                                AllocationMethod.RELATIVE_MRP,
                                List.of(new ImportLine("NOPRICE", "No price", 1, 40_000, null,
                                        "BOX-N", null, null))))));
        UUID lotId = lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b)
                .orElseThrow().getId();
        counting.countExpected(expectedLines.findByLotIdOrderByCode(lotId).get(0).getId(),
                StockCondition.GOOD, 1, Money.ofPaise(99_900), false, AT);

        CartView cart = checkout.open();
        UUID cartId = cart.cartId();
        assertThatThrownBy(() -> checkout.scan(cartId, "NOPRICE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no price");
    }

    @Test
    @DisplayName("An unknown code is refused with a plain message")
    void unknownCodeRefused() {
        CartView cart = checkout.open();
        UUID cartId = cart.cartId();
        assertThatThrownBy(() -> checkout.scan(cartId, "NEVERSEEN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nothing scans");
    }

    @Test
    @DisplayName("A priced product sells before its label has printed")
    void sellsBeforeTheLabelHasPrinted() {
        // onTheShelf prints no label — it is priced with a confirmed MRP, nothing more. The till
        // must sell it anyway; a sale cannot depend on the async label print having succeeded.
        String code = onTheShelf("EARLY", 30_000, 60_000, 25_000);

        CartView cart = checkout.scan(checkout.open().cartId(), code);

        assertThat(cart.lines()).hasSize(1);
        assertThat(cart.subtotalPaise()).isEqualTo(25_000);
    }

    @Test
    @DisplayName("A priced product with no MRP still sells, at its price, with no saving shown")
    void sellsWithoutAnMrp() {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Sushil"), "2026-07-17",
                        List.of(new ImportLot("HOME_ESSENTIALS", 10_000,
                                AllocationMethod.RELATIVE_MRP,
                                List.of(new ImportLine("NOMRP", "No mrp", 1, 40_000, null,
                                        "BOX-M", null, null))))));
        UUID lotId = lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b)
                .orElseThrow().getId();
        ExpectedLine line = expectedLines.findByLotIdOrderByCode(lotId).get(0);
        // Counted with NO MRP, then priced. A sale needs only a price.
        counting.countExpected(line.getId(), StockCondition.GOOD, 1, null, false, AT);
        pricing.setSellingPrice(line.getProduct().getId(), Money.ofPaise(29_900));

        CartView cart = checkout.scan(checkout.open().cartId(), "NOMRP");

        assertThat(cart.lines()).hasSize(1);
        assertThat(cart.lines().get(0).unitPricePaise()).isEqualTo(29_900);
        assertThat(cart.lines().get(0).savingPaise()).as("no MRP, so no saving").isZero();
        assertThat(cart.savingPaise()).isZero();
    }
}
