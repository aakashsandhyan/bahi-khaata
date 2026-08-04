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
import com.bahikhaata.contracts.PaymentMethod;
import com.bahikhaata.contracts.SaleSummary;
import com.bahikhaata.contracts.SaleView;
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
    @Autowired private SaleRepository sales;
    @Autowired private com.bahikhaata.backend.inventory.StockLevels stock;

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

    private UUID productIdOf(String code) {
        return barcodes.findByCode(code).orElseThrow().getProduct().getId();
    }

    @Test
    @DisplayName("Completing a cart records the sale, snapshots the lines, and decrements stock")
    void completeRecordsSaleAndDecrementsStock() {
        String code = onTheShelf("KADAI", 40_000, 99_900, 49_900); // 5 on hand, MRP 999, price 499
        UUID productId = productIdOf(code);
        UUID cartId = checkout.open().cartId();
        checkout.scan(cartId, code);
        checkout.scan(cartId, code); // quantity 2

        SaleView sale = checkout.toView(checkout.complete(cartId, PaymentMethod.CASH, "Ravi"), false);

        assertThat(sale.billNo()).isEqualTo(1);
        assertThat(sale.billNoFormatted()).isEqualTo("BB-000001");
        assertThat(sale.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(sale.operatorName()).isEqualTo("Ravi");
        assertThat(sale.subtotalPaise()).isEqualTo(2 * 49_900);
        assertThat(sale.totalPaise()).isEqualTo(2 * 49_900); // composition: no tax added
        assertThat(sale.taxPaise()).isZero();
        assertThat(sale.savingPaise()).isEqualTo(2 * 50_000);
        assertThat(sale.printFailed()).isFalse();
        assertThat(sale.lines()).singleElement().satisfies(l -> {
            assertThat(l.name()).isNotBlank();
            assertThat(l.unitPricePaise()).isEqualTo(49_900);
            assertThat(l.mrpPaise()).isEqualTo(99_900);
            assertThat(l.quantity()).isEqualTo(2);
            assertThat(l.lineTotalPaise()).isEqualTo(2 * 49_900);
        });
        // Stock fell from 5 to 3 through the SALE ledger.
        assertThat(stock.onHand(productId)).isEqualTo(3);
        assertThat(sales.findByBillNo(1).orElseThrow().getTotal().paise()).isEqualTo(2 * 49_900);
    }

    @Test
    @DisplayName("Bill numbers run consecutively across sales")
    void billNumbersIncrement() {
        String code = onTheShelf("MUG", 20_000, 30_000, 19_900);
        long first = checkout.complete(scannedCart(code), PaymentMethod.UPI, null).getBillNo();
        long second = checkout.complete(scannedCart(code), PaymentMethod.CARD, null).getBillNo();

        assertThat(second).isEqualTo(first + 1);
    }

    @Test
    @DisplayName("A later price change does not alter a past bill")
    void aPriceChangeDoesNotAlterAPastSale() {
        String code = onTheShelf("PAN", 30_000, 60_000, 29_900);
        UUID productId = productIdOf(code);
        long billNo = checkout.complete(scannedCart(code), PaymentMethod.CASH, null).getBillNo();

        pricing.setSellingPrice(productId, Money.ofPaise(39_900)); // reprice after the sale (below MRP)

        // The stored sale line still shows the price billed, not the new one.
        assertThat(checkout.toView(sales.findByBillNo(billNo).orElseThrow(), false)
                        .lines().get(0).unitPricePaise())
                .isEqualTo(29_900);
    }

    @Test
    @DisplayName("An empty cart cannot be completed")
    void emptyCartIsRejected() {
        UUID cartId = checkout.open().cartId();
        assertThatThrownBy(() -> checkout.complete(cartId, PaymentMethod.CASH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A completed cart cannot be completed again")
    void reCompletionIsRejected() {
        String code = onTheShelf("TRAY", 20_000, 40_000, 19_900);
        UUID cartId = scannedCart(code);
        checkout.complete(cartId, PaymentMethod.CASH, null);

        assertThatThrownBy(() -> checkout.complete(cartId, PaymentMethod.CASH, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Selling more than is counted still completes and drives on-hand negative")
    void overSellingCompletesAndGoesNegative() {
        String code = onTheShelf("LAMP", 50_000, 90_000, 49_900); // 5 counted
        UUID productId = productIdOf(code);
        UUID cartId = checkout.open().cartId();
        checkout.scan(cartId, code);
        UUID lineId = checkout.view(cartId).lines().get(0).lineId();
        checkout.setQuantity(cartId, lineId, 7); // sell 7 of 5

        checkout.complete(cartId, PaymentMethod.CASH, null);

        assertThat(stock.onHand(productId)).isEqualTo(-2);
    }

    @Test
    @DisplayName("The sales list is newest-first, and a sale is fetchable by bill number for reprint")
    void salesListAndLookup() {
        String a = onTheShelf("BOWL", 20_000, 40_000, 19_900);
        String b = onTheShelf("SPOON", 10_000, 20_000, 9_900);
        long firstBill = checkout.complete(scannedCart(a), PaymentMethod.CASH, "Ravi").getBillNo();
        long secondBill = checkout.complete(scannedCart(b), PaymentMethod.UPI, "Sita").getBillNo();

        List<SaleSummary> recent = checkout.recentSales(10);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).billNo()).as("newest first").isEqualTo(secondBill);
        assertThat(recent.get(0).paymentMethod()).isEqualTo(PaymentMethod.UPI);
        assertThat(recent.get(0).itemCount()).isEqualTo(1);

        SaleView fetched = checkout.saleByBillNo(firstBill);
        assertThat(fetched.operatorName()).isEqualTo("Ravi");
        assertThat(fetched.lines()).hasSize(1);
    }

    @Test
    @DisplayName("Fetching an unknown bill number is a plain not-found")
    void unknownBillNumberRejected() {
        assertThatThrownBy(() -> checkout.saleByBillNo(9999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No sale numbered");
    }

    private UUID scannedCart(String code) {
        UUID cartId = checkout.open().cartId();
        checkout.scan(cartId, code);
        return cartId;
    }
}
