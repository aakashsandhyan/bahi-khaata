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

import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CostAnchor;
import com.bahikhaata.contracts.CostBasisStrategy;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.Marketplace;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.MultiplierBase;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * How a lot's declared cost basis is wired into receiving: FLAT and an entered-cost MULTIPLIER
 * pin the moment a batch is created, an MRP-anchored basis pins at counting once the MRP is
 * recorded, an ASP-anchored basis pins the moment the online price is observed — even for a
 * batch counted before that observation ever happened — and a batch whose input is not yet known
 * stays uncosted (never zero) and still sellable. Alongside all of that, a lot with no declared
 * basis is unchanged: the manifest-rate pin from the existing stock-ledger spec still governs it,
 * proven by exercising both kinds of lot side by side in one test.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-cost-basis.db")
@Transactional
class LotCostBasisReceivingTest {

    private static final Instant AT = Instant.parse("2026-08-08T09:00:00Z");

    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private LotRepository lots;
    @Autowired private SupplierRepository suppliers;
    @Autowired private ProductRepository products;
    @Autowired private BarcodeRepository barcodes;
    @Autowired private BoxRepository boxes;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BatchRepository batches;
    @Autowired private StockLevels stock;

    private Supplier supplier(String name) {
        return suppliers.save(new Supplier(name, null, null, null, null, null));
    }

    private Lot manualLot(String supplierName) {
        return lots.save(
                new Lot(
                        supplier(supplierName),
                        LocalDate.of(2026, 8, 1),
                        Money.ofRupees(10_000),
                        Money.ZERO,
                        AllocationMethod.RELATIVE_MRP,
                        true));
    }

    private Product product(String name) {
        return products.save(new Product(name, Category.of("KITCHEN"), Map.of()));
    }

    /** Builds a manifest line the way {@code LotController.addProductToLot} would, for a manual lot. */
    private ExpectedLine expectedLine(Lot lot, Product product, String code, long quantity, Money statedValue) {
        Box box = boxes.save(new Box(lot, "BOX-" + code));
        return expectedLines.save(new ExpectedLine(lot, box, product, code, quantity, statedValue));
    }

    private Batch soleBatchFor(Lot lot, Product product) {
        return batches.findByLotIdAndProductId(lot.getId(), product.getId()).stream()
                .findFirst()
                .orElseThrow();
    }

    @Test
    void flatPerUnitPinsTheMomentTheBatchIsCreated() {
        Lot lot = manualLot("Flat Supplier");
        lot.setCostBasisStrategy(CostBasisStrategy.FLAT_PER_UNIT);
        lot.setFlatUnitCost(Money.ofPaise(8_00));
        lots.save(lot);

        Product product = product("Steel bowl");
        Batch batch = counting.receiveManual(lot, product, StockCondition.GOOD, 5, null, false, AT);

        assertThat(batch.isCosted()).isTrue();
        assertThat(batch.getAllocatedUnitCost()).isEqualTo(Money.ofPaise(8_00));
        assertThat(batch.getAllocatedTotal()).isEqualTo(Money.ofPaise(40_00));
    }

    @Test
    void multiplierOnAnEnteredCostPinsImmediately() {
        Lot lot = manualLot("Multiplier Supplier");
        lot.setCostBasisStrategy(CostBasisStrategy.MULTIPLIER);
        lot.setMultiplierMilli(1_500L); // 1.5x
        lot.setMultiplierBase(MultiplierBase.ENTERED_UNIT_COST);
        lot.setFlatUnitCost(Money.ofPaise(2_00));
        lots.save(lot);

        Product product = product("Wooden spoon");
        Batch batch = counting.receiveManual(lot, product, StockCondition.GOOD, 3, null, false, AT);

        assertThat(batch.isCosted()).isTrue();
        assertThat(batch.getAllocatedUnitCost()).isEqualTo(Money.ofPaise(3_00)); // 2.00 * 1.5
    }

    @Test
    void mrpAnchoredBasisIsUncostedUntilTheMrpIsRecordedThenPinsAtCounting() {
        Lot lot = manualLot("Percent Supplier");
        lot.setCostBasisStrategy(CostBasisStrategy.PERCENT_OF_ANCHOR);
        lot.setCostAnchor(CostAnchor.MRP);
        lot.setPercentBp(4_000L); // 40%
        lots.save(lot);

        Product product = product("Ceramic mug");
        ExpectedLine line = expectedLine(lot, product, "MUG", 10, null);

        // Counted with no MRP yet: the anchor is not known, so the batch stays uncosted — but
        // still sellable stock, on hand at once.
        counting.countExpected(line.getId(), 6, null, false, AT);
        Batch batch = soleBatchFor(lot, product);
        assertThat(batch.isCosted())
                .as("an MRP-anchored basis has nothing to derive from yet")
                .isFalse();
        assertThat(stock.onHand(product.getId()))
                .as("uncosted stock is still real, sellable stock")
                .isEqualTo(6);

        // The MRP is read off the goods on a later count of the same product: the anchor is now
        // known, and the basis pins at once — no lot close required.
        counting.countExpected(line.getId(), 4, Money.ofPaise(500_00), false, AT);
        batch = soleBatchFor(lot, product);
        assertThat(batch.isCosted()).isTrue();
        assertThat(batch.getAllocatedUnitCost()).isEqualTo(Money.ofPaise(200_00)); // 40% of 500.00
        assertThat(batch.getQuantityReceived()).isEqualTo(10);
    }

    @Test
    void rateRangeLeavesAnOutOfBandItemUncostedRatherThanGuessing() {
        Lot lot = manualLot("Rate Card Supplier");
        lot.setCostBasisStrategy(CostBasisStrategy.MRP_RATE_RANGE);
        lot.setCostAnchor(CostAnchor.MRP);
        lots.save(lot);
        // Deliberately no bands saved: every MRP falls outside an empty rate card, the same as
        // falling outside every band of a non-empty one.

        Product product = product("Steel tumbler");
        ExpectedLine line = expectedLine(lot, product, "TUMBLER", 2, null);

        counting.countExpected(line.getId(), 2, Money.ofPaise(150_00), false, AT);

        Batch batch = soleBatchFor(lot, product);
        assertThat(batch.isCosted())
                .as("an MRP outside every band is left uncosted and flagged, not guessed")
                .isFalse();
        assertThat(stock.onHand(product.getId())).isEqualTo(2);
    }

    @Test
    void multiplierOnTheManifestStatedValuePinsAtCounting() {
        Lot lot = manualLot("Stated Value Multiplier Supplier");
        lot.setCostBasisStrategy(CostBasisStrategy.MULTIPLIER);
        lot.setMultiplierMilli(1_200L); // 1.2x
        lot.setMultiplierBase(MultiplierBase.STATED_VALUE);
        lots.save(lot);

        Product product = product("Plastic tray");
        ExpectedLine line = expectedLine(lot, product, "TRAY", 5, Money.ofPaise(100_00));

        counting.countExpected(line.getId(), 5, null, false, AT);

        Batch batch = soleBatchFor(lot, product);
        assertThat(batch.isCosted()).isTrue();
        assertThat(batch.getAllocatedUnitCost()).isEqualTo(Money.ofPaise(120_00)); // 100 * 1.2
    }

    @Test
    void aspAnchoredBasisPinsTheMomentTheOnlinePriceIsObservedEvenForAnEarlierUncostedBatch() {
        Lot lot = manualLot("ASP Supplier");
        lot.setCostBasisStrategy(CostBasisStrategy.PERCENT_OF_ANCHOR);
        lot.setCostAnchor(CostAnchor.ASP);
        lot.setPercentBp(2_000L); // 20%
        lots.save(lot);

        Product product = product("Widget");
        barcodes.save(new com.bahikhaata.backend.catalog.Barcode(product, "WIDGET", com.bahikhaata.contracts.Origin.MANUFACTURER));
        ExpectedLine line = expectedLine(lot, product, "WIDGET", 3, null);

        // Counted before anyone has ever observed what this sells for online: the ASP anchor is
        // unknown, so the batch is left uncosted.
        counting.countExpected(line.getId(), 3, null, false, AT);
        assertThat(soleBatchFor(lot, product).isCosted()).isFalse();

        // A later, unrelated consignment import of the same product (matched by its existing
        // barcode) states an online price, observing the ASP for the first time. That alone —
        // no further count against this lot — pins the batch counted earlier.
        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplier("Returns Marketplace").getId().toString(),
                        "2026-08-08",
                        List.of(
                                new ImportLot(
                                        "KITCHEN",
                                        50_000,
                                        AllocationMethod.RELATIVE_MRP,
                                        List.of(
                                                new ImportLine(
                                                        "WIDGET", "Widget", 1, 500_00, null, "BOX-Z", 500_00L,
                                                        Marketplace.AMAZON))))));

        Batch batch = soleBatchFor(lot, product);
        assertThat(batch.isCosted())
                .as("the ASP anchor just became known; this basis pins from it without waiting"
                        + " on another count")
                .isTrue();
        assertThat(batch.getAllocatedUnitCost()).isEqualTo(Money.ofPaise(100_00)); // 20% of 500.00
    }

    @Test
    void aLotWithNoDeclaredBasisKeepsTheManifestRatePinUnaffectedByALotThatHasOne() {
        // A cost-basis lot alongside an ordinary one, so the two paths are proven not to bleed
        // into each other — the regression the change's design insists on (task 8.5).
        Lot basisLot = manualLot("Basis Supplier");
        basisLot.setCostBasisStrategy(CostBasisStrategy.FLAT_PER_UNIT);
        basisLot.setFlatUnitCost(Money.ofPaise(50_00));
        lots.save(basisLot);
        Product basisProduct = product("Basis product");
        counting.receiveManual(basisLot, basisProduct, StockCondition.GOOD, 2, null, false, AT);

        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplier("Legacy Supplier").getId().toString(),
                        "2026-08-08",
                        List.of(
                                new ImportLot(
                                        "KITCHEN",
                                        100_000,
                                        AllocationMethod.RELATIVE_MRP,
                                        List.of(
                                                new ImportLine(
                                                        "KADAI", "Steel kadai", 12, 10_000, null,
                                                        "BOX-A", null, null))))));
        Lot legacyLot =
                lots.findAll().stream()
                        .filter(l -> !l.getId().equals(basisLot.getId()))
                        .filter(l -> !l.declaresCostBasis())
                        .findFirst()
                        .orElseThrow();

        ExpectedLine legacyLine = expectedLines.findByLotIdOrderByCode(legacyLot.getId()).get(0);
        counting.countExpected(legacyLine.getId(), 12, null, false, AT);

        Batch legacyBatch = soleBatchFor(legacyLot, legacyLine.getProduct());
        assertThat(legacyBatch.getAllocatedUnitCost())
                // rate = amountPaid(100000) / statedTotal(10000*12=120000); unit cost = rate *
                // 10000 = 1,000,000,000 / 120,000 = 8333.33..., HALF_UP to 8333 — unchanged
                // legacy math, computed here in integers rather than float to avoid a rounding
                // mismatch against production's BigDecimal HALF_UP.
                .as("unchanged legacy rate math")
                .isEqualTo(Money.ofPaise(8_333));

        Batch basisBatch = soleBatchFor(basisLot, basisProduct);
        assertThat(basisBatch.getAllocatedUnitCost())
                .as("the cost-basis lot's flat cost is unaffected by the legacy lot beside it")
                .isEqualTo(Money.ofPaise(50_00));
    }
}
