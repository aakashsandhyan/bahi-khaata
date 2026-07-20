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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-lot-freeze.db")
@Transactional
class LotEditPolicyTest {

    private static final Instant WHEN = Instant.parse("2026-07-20T10:00:00Z");

    @Autowired
    private ProductRepository products;

    @Autowired
    private LotRepository lots;

    @Autowired
    private BatchRepository batches;

    @Autowired
    private StockLedgerRepository ledger;

    @Autowired
    private LotEditPolicy policy;

    private Lot newLot() {
        return lots.save(
                new Lot(
                        "Liquidator A",
                        LocalDate.of(2026, 7, 1),
                        Money.ofRupees(50_000),
                        Money.ZERO,
                        AllocationMethod.RELATIVE_MRP));
    }

    private Batch stockedBatch(Lot lot, String productName, long quantity) {
        Product product = products.save(new Product(productName, Category.KITCHEN, Map.of()));
        Batch batch =
                batches.save(
                        new Batch(
                                product, lot, Money.ofRupees(120), CostBasis.ALLOCATED,
                                quantity, 0, Money.ofRupees(300), false));
        ledger.save(StockLedgerEntry.receiptOf(batch, WHEN));
        ledger.flush();
        return batch;
    }

    @Test
    @DisplayName("A lot with only receipts is still editable")
    void receiptsAloneDoNotFreeze() {
        Lot lot = newLot();
        stockedBatch(lot, "Steel kettle", 100);

        // Goods have arrived but nothing has been sold: this is still just a data-entry
        // record, and a mistyped amount should be fixable.
        assertThat(policy.isFrozen(lot.getId())).isFalse();
        assertThatCode(() -> policy.requireEditable(lot.getId())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A lot with no movements at all is editable")
    void emptyLotIsEditable() {
        assertThat(policy.isFrozen(newLot().getId())).isFalse();
    }

    @Test
    @DisplayName("Selling from a lot freezes it")
    void saleFreezesTheLot() {
        Lot lot = newLot();
        Batch batch = stockedBatch(lot, "Steel kettle", 100);

        ledger.save(
                StockLedgerEntry.sale(
                        batch.getProduct(), batch, 2, Money.ofRupees(240), WHEN));
        ledger.flush();

        assertThat(policy.isFrozen(lot.getId())).isTrue();
        assertThatThrownBy(() -> policy.requireEditable(lot.getId()))
                .isInstanceOf(LotFrozenException.class)
                .hasMessageContaining(lot.getId().toString())
                .hasMessageContaining("adjustment");
    }

    @Test
    @DisplayName("Consuming from one batch freezes the whole lot, not just that batch")
    void consumptionFromOneBatchFreezesEveryBatch() {
        Lot lot = newLot();
        Batch kettles = stockedBatch(lot, "Steel kettle", 100);
        Batch bottles = stockedBatch(lot, "Steel bottle", 50);

        // Only the kettles have sold.
        ledger.save(
                StockLedgerEntry.sale(
                        kettles.getProduct(), kettles, 1, Money.ofRupees(120), WHEN));
        ledger.flush();

        // The bottles are untouched, but their cost came from the same apportioned amount:
        // editing the lot would re-cost them, changing the basis of stock already sold.
        assertThat(ledger.quantityOnHandForBatch(bottles.getId())).isEqualTo(50);
        assertThat(policy.isFrozen(lot.getId())).isTrue();
    }

    @Test
    @DisplayName("A write-off freezes too — stock has left either way")
    void writeOffFreezesTheLot() {
        Lot lot = newLot();
        Batch batch = stockedBatch(lot, "Damaged goods", 20);

        ledger.save(StockLedgerEntry.writeOff(batch.getProduct(), batch, 3, WHEN));
        ledger.flush();

        assertThat(policy.isFrozen(lot.getId())).isTrue();
    }

    @Test
    @DisplayName("One lot freezing leaves other lots editable")
    void freezingIsPerLot() {
        Lot sold = newLot();
        Lot untouched = newLot();
        Batch batch = stockedBatch(sold, "Steel kettle", 10);
        stockedBatch(untouched, "Wall clock", 10);

        ledger.save(
                StockLedgerEntry.sale(
                        batch.getProduct(), batch, 1, Money.ofRupees(120), WHEN));
        ledger.flush();

        assertThat(policy.isFrozen(sold.getId())).isTrue();
        assertThat(policy.isFrozen(untouched.getId())).isFalse();
    }
}
