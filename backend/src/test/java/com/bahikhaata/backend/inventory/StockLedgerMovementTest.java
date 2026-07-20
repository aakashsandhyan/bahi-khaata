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
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.MovementType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Direction lives in the sign of the quantity, and the movement type records why.
 *
 * <p>There is no separate direction column that could contradict the quantity, and no way to
 * book a movement in the wrong direction by hand: each kind has its own factory, the outward
 * ones take a positive count and record it negative.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-ledger-movement.db")
@Transactional
class StockLedgerMovementTest {

    private static final Instant WHEN = Instant.parse("2026-07-20T10:00:00Z");

    @Autowired
    private ProductRepository products;

    @Autowired
    private LotRepository lots;

    @Autowired
    private BatchRepository batches;

    @Autowired
    private StockLedgerRepository ledger;

    private Product product;
    private Batch batch;

    @BeforeEach
    void setUp() {
        product = products.save(new Product("Steel kettle", Category.KITCHEN, Map.of()));
        Lot lot =
                lots.save(
                        new Lot(
                                "Liquidator A",
                                LocalDate.of(2026, 7, 20),
                                Money.ofRupees(50_000),
                                Money.ZERO,
                                AllocationMethod.RELATIVE_MRP));
        batch =
                batches.save(
                        new Batch(
                                product, lot, Money.ofPaise(18_750), CostBasis.ALLOCATED,
                                100, 0, Money.ofRupees(300), false));
    }

    @Test
    @DisplayName("A receipt is positive and carries no cost of goods sold")
    void receiptIsPositive() {
        StockLedgerEntry entry =
                ledger.save(StockLedgerEntry.receipt(product, batch, 100, WHEN));

        assertThat(entry.getQuantity()).isEqualTo(100);
        assertThat(entry.getMovementType()).isEqualTo(MovementType.PURCHASE_RECEIPT);
        assertThat(entry.getCogs()).isNull();
    }

    @Test
    @DisplayName("A sale is negative, from a positive count, and carries its cost")
    void saleIsNegative() {
        StockLedgerEntry entry =
                ledger.save(
                        StockLedgerEntry.sale(product, batch, 2, Money.ofPaise(37_500), WHEN));

        // The caller passes what was sold; the ledger records the direction.
        assertThat(entry.getQuantity()).isEqualTo(-2);
        assertThat(entry.getMovementType()).isEqualTo(MovementType.SALE);
        assertThat(entry.getCogs()).isEqualTo(Money.ofPaise(37_500));
    }

    @Test
    @DisplayName("A write-off is negative and carries no cost of goods sold")
    void writeOffIsNegative() {
        StockLedgerEntry entry = ledger.save(StockLedgerEntry.writeOff(product, batch, 3, WHEN));

        assertThat(entry.getQuantity()).isEqualTo(-3);
        assertThat(entry.getMovementType()).isEqualTo(MovementType.WRITE_OFF);
        // Nothing was sold, so attributing cost of goods sold would overstate the cost of
        // what actually earned.
        assertThat(entry.getCogs()).isNull();
    }

    @Test
    @DisplayName("An adjustment may go either way — a stock take can come out high or low")
    void adjustmentGoesEitherWay() {
        assertThat(ledger.save(StockLedgerEntry.adjustment(product, batch, 5, WHEN)).getQuantity())
                .isEqualTo(5);
        assertThat(ledger.save(StockLedgerEntry.adjustment(product, batch, -4, WHEN)).getQuantity())
                .isEqualTo(-4);
    }

    @Test
    @DisplayName("Outward movements cannot be booked as inward by passing a negative count")
    void outwardMovementsRefuseNonPositiveCounts() {
        // The trap this closes: a caller passing -2 to sale(), meaning "two out", and the
        // negation turning it into a phantom receipt that invents stock.
        assertThatThrownBy(
                        () -> StockLedgerEntry.sale(product, batch, -2, Money.ofPaise(100), WHEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockLedgerEntry.writeOff(product, batch, -3, WHEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockLedgerEntry.receipt(product, batch, -1, WHEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A movement of zero is refused, whichever kind")
    void zeroMovementsRefused() {
        assertThatThrownBy(() -> StockLedgerEntry.receipt(product, batch, 0, WHEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockLedgerEntry.adjustment(product, batch, 0, WHEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A sale without its cost is refused")
    void saleRequiresCogs() {
        // A sale whose cost is unknown would silently break margin reporting.
        assertThatThrownBy(() -> StockLedgerEntry.sale(product, batch, 2, null, WHEN))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Movement kinds are distinguishable when reconciling")
    void movementKindsAreDistinguishable() {
        ledger.save(StockLedgerEntry.receipt(product, batch, 100, WHEN));
        ledger.save(StockLedgerEntry.sale(product, batch, 2, Money.ofPaise(37_500), WHEN));
        ledger.save(StockLedgerEntry.writeOff(product, batch, 1, WHEN));

        // "Three fewer kettles" is not useful; "two sold, one written off" is.
        assertThat(ledger.findByBatchId(batch.getId()))
                .extracting(StockLedgerEntry::getMovementType)
                .containsExactlyInAnyOrder(
                        MovementType.PURCHASE_RECEIPT, MovementType.SALE, MovementType.WRITE_OFF);
    }
}
