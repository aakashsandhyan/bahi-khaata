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

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.InventoryDetail;
import com.bahikhaata.contracts.InventoryRow;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.StockCondition;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Inventory screen's aggregate math and detail composition, against a real database — the
 * scenarios of the {@code inventory-view} and {@code item-detail} specs (design decisions D1–D4
 * of palletworks-inventory).
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-inventory-service.db")
@Transactional
class InventoryServiceTest {

    @Autowired private InventoryService inventory;
    @Autowired private ProductRepository products;
    @Autowired private LotRepository lots;
    @Autowired private BatchRepository batches;
    @Autowired private StockLedgerRepository ledger;
    // InventoryQueryRepository reads through raw JdbcTemplate, on the same connection as this
    // test's JPA writes but not through the same session — Hibernate only flushes pending inserts
    // to that connection at points it chooses, never automatically before a plain JDBC read. Every
    // fixture-building test flushes explicitly before calling into inventory.rows()/detail() so the
    // aggregate query sees what was just built rather than racing Hibernate's own flush timing.
    @Autowired private EntityManager entityManager;

    private Lot lot(String supplier, LocalDate receivedOn) {
        return lots.save(
                new Lot(supplier, receivedOn, Money.ofRupees(50_000), Money.ZERO, AllocationMethod.RELATIVE_MRP));
    }

    /**
     * A costed batch in the given condition. {@code Batch}'s multi-arg constructor (used
     * elsewhere for already-costed fixtures) always builds GOOD; a condition-bearing costed batch
     * needs the {@code counted} factory followed by {@link Batch#applyAllocation} — both public,
     * ordinary API, the same path a real lot close takes.
     */
    private Batch costedBatch(
            Product product, Lot lot, StockCondition condition, long unitCostPaise, long quantity) {
        Batch batch = Batch.counted(product, lot, condition, quantity, Money.ofRupees(999), false);
        batch.applyAllocation(
                Money.ofPaise(unitCostPaise * quantity), Money.ofPaise(unitCostPaise), CostBasis.ALLOCATED);
        return batches.save(batch);
    }

    private void receipt(Product product, Batch batch, long quantity, Instant effectiveAt) {
        ledger.save(StockLedgerEntry.receipt(product, batch, quantity, effectiveAt));
    }

    private void sale(Product product, Batch batch, long quantity, Instant effectiveAt) {
        ledger.save(
                StockLedgerEntry.sale(
                        product, batch, quantity, Money.ofPaise(1), effectiveAt));
    }

    @Test
    @DisplayName("A product held in two conditions yields two rows")
    void twoConditionsYieldTwoRows() {
        Product product = products.save(new Product("Two-Condition Kettle", Category.of("KITCHEN"), Map.of()));
        Lot lot = lot("Supplier Two-Cond", LocalDate.of(2026, 7, 1));
        Batch good = costedBatch(product, lot, StockCondition.GOOD, 10_000, 5);
        Batch damaged = costedBatch(product, lot, StockCondition.DAMAGED, 10_000, 2);
        receipt(product, good, 5, Instant.parse("2026-07-01T10:00:00Z"));
        receipt(product, damaged, 2, Instant.parse("2026-07-01T10:00:00Z"));

        List<InventoryRow> rows = rows().stream()
                .filter(r -> r.productId().equals(product.getId()))
                .toList();

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(InventoryRow::condition)
                .containsExactlyInAnyOrder("GOOD", "DAMAGED");
        InventoryRow goodRow = rows.stream().filter(r -> r.condition().equals("GOOD")).findFirst().orElseThrow();
        InventoryRow damagedRow = rows.stream().filter(r -> r.condition().equals("DAMAGED")).findFirst().orElseThrow();
        assertThat(goodRow.onHandQuantity()).isEqualTo(5);
        assertThat(damagedRow.onHandQuantity()).isEqualTo(2);
        // The Inventory screen's department filter (palletworks-nav) reads this off the same
        // joined product row that already supplies productName — no extra join.
        assertThat(goodRow.categoryCode()).isEqualTo("KITCHEN");
    }

    @Test
    @DisplayName("Lot rollup: a single lot shows its identity, more than one shows an 'N lots' marker")
    void lotRollupCollapsesMultipleLots() {
        Product single = products.save(new Product("Single-Lot Toaster", Category.of("KITCHEN"), Map.of()));
        Lot lotA = lot("Rollup Supplier A", LocalDate.of(2026, 7, 5));
        Batch batchA = costedBatch(single, lotA, StockCondition.GOOD, 5_000, 3);
        receipt(single, batchA, 3, Instant.parse("2026-07-05T09:00:00Z"));

        Product multi = products.save(new Product("Multi-Lot Toaster", Category.of("KITCHEN"), Map.of()));
        Lot lotB = lot("Rollup Supplier B", LocalDate.of(2026, 7, 6));
        Lot lotC = lot("Rollup Supplier C", LocalDate.of(2026, 7, 7));
        Batch batchB = costedBatch(multi, lotB, StockCondition.GOOD, 5_000, 2);
        Batch batchC = costedBatch(multi, lotC, StockCondition.GOOD, 6_000, 4);
        receipt(multi, batchB, 2, Instant.parse("2026-07-06T09:00:00Z"));
        receipt(multi, batchC, 4, Instant.parse("2026-07-07T09:00:00Z"));

        InventoryRow singleRow = rowFor(single.getId());
        InventoryRow multiRow = rowFor(multi.getId());

        assertThat(singleRow.lotLabel()).contains("Rollup Supplier A");
        assertThat(multiRow.lotLabel()).isEqualTo("2 lots");
    }

    @Test
    @DisplayName("Age is whole days since the product's first PURCHASE_RECEIPT")
    void ageIsDaysSinceFirstReceipt() {
        Product product = products.save(new Product("Aging Blender", Category.of("KITCHEN"), Map.of()));
        Lot lot = lot("Aging Supplier", LocalDate.of(2026, 6, 1));
        Batch batch = costedBatch(product, lot, StockCondition.GOOD, 8_000, 6);
        // A little over 45 days ago, so a slow test run cannot round it down to 44.
        Instant firstReceipt = Instant.now().minus(Duration.ofDays(45)).minusSeconds(30);
        receipt(product, batch, 6, firstReceipt);

        InventoryRow row = rowFor(product.getId());

        assertThat(row.ageDays()).isEqualTo(45);
    }

    @Test
    @DisplayName("Zero-stock groups are excluded from the inventory rows")
    void zeroStockGroupsExcluded() {
        Product product = products.save(new Product("Sold-Out Fan", Category.of("ELECTRONICS"), Map.of()));
        Lot lot = lot("Zero-Stock Supplier", LocalDate.of(2026, 7, 10));
        Batch batch = costedBatch(product, lot, StockCondition.GOOD, 12_000, 3);
        receipt(product, batch, 3, Instant.parse("2026-07-10T09:00:00Z"));
        sale(product, batch, 3, Instant.parse("2026-07-11T09:00:00Z"));

        // A control product with genuine stock in the same run, so this proves real exclusion
        // rather than an empty result that would pass just as well if the query returned nothing.
        Product control = products.save(new Product("Still-Stocked Fan", Category.of("ELECTRONICS"), Map.of()));
        Batch controlBatch = costedBatch(control, lot, StockCondition.GOOD, 12_000, 2);
        receipt(control, controlBatch, 2, Instant.parse("2026-07-10T09:00:00Z"));

        List<InventoryRow> rows = rows();
        assertThat(rows).noneMatch(r -> r.productId().equals(product.getId()));
        assertThat(rows).anyMatch(r -> r.productId().equals(control.getId()));
    }

    @Test
    @DisplayName("Margin is derived from price and cost basis, not stored")
    void marginDerivedFromPriceAndCostBasis() {
        Product product = products.save(new Product("Margin Cooker", Category.of("KITCHEN"), Map.of()));
        product.setSellingPrice(Money.ofPaise(20_000)); // cost 10,000 -> 50% gross margin
        products.save(product);
        Lot lot = lot("Margin Supplier", LocalDate.of(2026, 7, 12));
        Batch batch = costedBatch(product, lot, StockCondition.GOOD, 10_000, 4);
        receipt(product, batch, 4, Instant.parse("2026-07-12T09:00:00Z"));

        InventoryRow row = rowFor(product.getId());

        assertThat(row.costBasisPaise()).isEqualTo(10_000L);
        assertThat(row.sellingPricePaise()).isEqualTo(20_000L);
        assertThat(row.marginPercent()).isEqualTo(50);
    }

    @Test
    @DisplayName("An unpriced product's row carries a null price, never zero")
    void unpricedProductRowHasNullPrice() {
        // Regression: wasNull() reports on the most recent column read, so capturing it lines
        // after getLong("price_paise") silently turned a null price into 0 on screen.
        Product product = products.save(new Product("Unpriced Kettle", Category.of("KITCHEN"), Map.of()));
        Lot lot = lot("Unpriced Supplier", LocalDate.of(2026, 7, 14));
        Batch batch = costedBatch(product, lot, StockCondition.GOOD, 10_000, 2);
        receipt(product, batch, 2, Instant.parse("2026-07-14T09:00:00Z"));

        InventoryRow row = rowFor(product.getId());

        assertThat(row.sellingPricePaise()).isNull();
        assertThat(row.marginPercent()).isNull();
    }

    @Test
    @DisplayName("An uncosted contributing batch leaves cost basis and margin honestly unknown")
    void uncostedBatchLeavesCostBasisNull() {
        Product product = products.save(new Product("Uncosted Grill", Category.of("KITCHEN"), Map.of()));
        product.setSellingPrice(Money.ofPaise(30_000));
        products.save(product);
        Lot lot = lot("Uncosted Supplier", LocalDate.of(2026, 7, 13));
        // Counted but not yet allocated a cost: the `counted` factory, not the costed constructor.
        Batch uncosted = batches.save(Batch.counted(product, lot, 3, Money.ofRupees(999), false));
        receipt(product, uncosted, 3, Instant.parse("2026-07-13T09:00:00Z"));

        InventoryRow row = rowFor(product.getId());

        assertThat(row.costBasisPaise()).as("a partly-uncosted group must not show a false cost").isNull();
        assertThat(row.marginPercent()).isNull();
    }

    @Test
    @DisplayName("Item detail composes batches, movements, and price history for one product")
    void detailComposesBatchesMovementsAndPriceHistory() {
        Product product = products.save(new Product("Detail Kettle", Category.of("KITCHEN"), Map.of()));
        product.setSellingPrice(Money.ofPaise(25_000));
        products.save(product);
        Lot lot = lot("Detail Supplier", LocalDate.of(2026, 7, 14));
        Batch batch = costedBatch(product, lot, StockCondition.GOOD, 10_000, 5);
        receipt(product, batch, 5, Instant.parse("2026-07-14T09:00:00Z"));
        sale(product, batch, 1, Instant.parse("2026-07-15T09:00:00Z"));

        Optional<InventoryDetail> found = detail(product.getId());

        assertThat(found).isPresent();
        InventoryDetail detail = found.orElseThrow();
        assertThat(detail.receivedUnits()).isEqualTo(5);
        assertThat(detail.soldUnits()).isEqualTo(1);
        assertThat(detail.batches()).hasSize(1);
        assertThat(detail.batches().get(0).batchId()).isEqualTo(batch.getId());
        assertThat(detail.movements()).hasSize(2);
        // Newest first: the sale (later effective time) before the receipt.
        assertThat(detail.movements().get(0).movementType()).isEqualTo("SALE");
        assertThat(detail.movements().get(1).movementType()).isEqualTo("PURCHASE_RECEIPT");
    }

    @Test
    @DisplayName("Detail for an unknown product is empty")
    void detailForUnknownProductIsEmpty() {
        assertThat(inventory.detail(java.util.UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Setting a batch's bin persists it")
    void setBinPersists() {
        Product product = products.save(new Product("Bin Cooker", Category.of("KITCHEN"), Map.of()));
        product.setSellingPrice(Money.ofPaise(30_000));
        products.save(product);
        Lot lot = lot("Bin Supplier", LocalDate.of(2026, 7, 16));
        Batch batch = costedBatch(product, lot, StockCondition.GOOD, 5_000, 2);

        Optional<Batch> result = inventory.setBin(batch.getId(), "A-01");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getBin()).isEqualTo("A-01");
        Batch reloaded = batches.findById(batch.getId()).orElseThrow();
        assertThat(reloaded.getBin()).isEqualTo("A-01");
        // Isolated bin change only (item-detail spec): neither price nor stock moves.
        assertThat(reloaded.getQuantityReceived()).isEqualTo(2);
        assertThat(reloaded.getAllocatedUnitCost()).isEqualTo(Money.ofPaise(5_000));
        assertThat(products.findById(product.getId()).orElseThrow().getSellingPrice())
                .isEqualTo(Money.ofPaise(30_000));
    }

    @Test
    @DisplayName("Setting a blank bin trims to NULL, not an empty string — and is still found, not a 404")
    void setBinTrimsBlankToNull() {
        Product product = products.save(new Product("Blank Bin Cooker", Category.of("KITCHEN"), Map.of()));
        Lot lot = lot("Blank Bin Supplier", LocalDate.of(2026, 7, 17));
        Batch batch = costedBatch(product, lot, StockCondition.GOOD, 5_000, 2);
        inventory.setBin(batch.getId(), "A-02");

        Optional<Batch> result = inventory.setBin(batch.getId(), "   ");

        // Presence proves this is "found, and cleared" — never mistaken for "no such batch",
        // which is exactly the Optional.map-with-a-nullable-mapper trap InventoryService avoids.
        assertThat(result).as("a cleared bin is still a found batch, not an absent one").isPresent();
        assertThat(result.orElseThrow().getBin()).isNull();
        assertThat(batches.findById(batch.getId()).orElseThrow().getBin()).isNull();
    }

    @Test
    @DisplayName("Setting the bin of an unknown batch is empty")
    void setBinForUnknownBatchIsEmpty() {
        assertThat(inventory.setBin(java.util.UUID.randomUUID(), "A-03")).isEmpty();
    }

    private List<InventoryRow> rows() {
        entityManager.flush();
        return inventory.rows();
    }

    private Optional<InventoryDetail> detail(java.util.UUID productId) {
        entityManager.flush();
        return inventory.detail(productId);
    }

    private InventoryRow rowFor(java.util.UUID productId) {
        return rows().stream()
                .filter(r -> r.productId().equals(productId))
                .findFirst()
                .orElseThrow();
    }
}
