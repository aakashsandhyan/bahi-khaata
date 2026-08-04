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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.LotState;
import com.bahikhaata.contracts.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Boxes, expected lines, lot state, and stock that is held before it is costed. */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-expectation.db")
@Transactional
class ExpectationMappingTest {

    @Autowired private ProductRepository products;
    @Autowired private LotRepository lots;
    @Autowired private BoxRepository boxes;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BatchRepository batches;

    private Lot newLot() {
        return lots.save(
                new Lot(
                        "Sushil", LocalDate.of(2026, 7, 17), Money.ofPaise(100_000), Money.ZERO,
                        AllocationMethod.RELATIVE_MRP));
    }

    private Product newProduct(String name) {
        return products.save(new Product(name, Category.of("KITCHEN"), Map.of()));
    }

    @Test
    @DisplayName("A box round-trips with its lot and tracking number")
    void boxRoundTrips() {
        Lot lot = newLot();
        Box saved = boxes.save(new Box(lot, "52109919050"));

        Box found = boxes.findById(saved.getId()).orElseThrow();
        assertThat(found.getTrackingNumber()).isEqualTo("52109919050");
        assertThat(found.getLot().getId()).isEqualTo(lot.getId());
        assertThat(found.isFinished()).isFalse();
    }

    @Test
    @DisplayName("An expected line round-trips, keeping its per-unit stated value")
    void expectedLineRoundTrips() {
        Lot lot = newLot();
        Box box = boxes.save(new Box(lot, "52109919050"));
        Product product = newProduct("Steel kadai");

        ExpectedLine saved =
                expectedLines.save(
                        new ExpectedLine(lot, box, product, "B0DBHYTM2X", 3, Money.ofPaise(12_000)));

        ExpectedLine found = expectedLines.findById(saved.getId()).orElseThrow();
        assertThat(found.getCode()).isEqualTo("B0DBHYTM2X");
        assertThat(found.getQuantityExpected()).isEqualTo(3);
        assertThat(found.getStatedValue()).isEqualTo(Money.ofPaise(12_000));
    }

    @Test
    @DisplayName("An expected line may state no value at all")
    void expectedLineMayHaveNoStatedValue() {
        Lot lot = newLot();
        Box box = boxes.save(new Box(lot, "BOX-1"));
        Product product = newProduct("Unpriced thing");

        ExpectedLine saved =
                expectedLines.save(new ExpectedLine(lot, box, product, "CODE-1", 1, null));

        assertThat(expectedLines.findById(saved.getId()).orElseThrow().getStatedValue())
                .as("a manifest may state neither a cost nor a market price")
                .isNull();
    }

    @Test
    @DisplayName("A counted batch holds stock with no cost, and null is not zero")
    void countedBatchIsUncosted() {
        Lot lot = newLot();
        Product product = newProduct("Counted but uncosted");

        Batch saved = batches.save(Batch.counted(product, lot, 11, null, false));

        Batch found = batches.findById(saved.getId()).orElseThrow();
        assertThat(found.getQuantityReceived()).isEqualTo(11);
        assertThat(found.isCosted()).isFalse();
        assertThat(found.getAllocatedTotal())
                .as("null means not yet apportioned; zero would report the stock as pure profit")
                .isNull();
        assertThat(found.getAllocatedUnitCost()).isNull();
        assertThat(found.getCostBasis()).isNull();
    }

    @Test
    @DisplayName("Applying an allocation costs a batch once and only once")
    void allocationAppliesOnce() {
        Lot lot = newLot();
        Batch batch = batches.save(Batch.counted(newProduct("Thing"), lot, 4, null, false));

        batch.applyAllocation(Money.ofPaise(40_000), Money.ofPaise(10_000), CostBasis.ALLOCATED);

        assertThat(batch.isCosted()).isTrue();
        assertThat(batch.getAllocatedUnitCost()).isEqualTo(Money.ofPaise(10_000));
        assertThatThrownBy(
                        () ->
                                batch.applyAllocation(
                                        Money.ofPaise(1), Money.ofPaise(1), CostBasis.ALLOCATED))
                .as("a cost may already have priced goods and recorded COGS")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already costed");
    }

    @Test
    @DisplayName("A lot opens on creation and closes once, one way")
    void lotOpensThenClosesOnce() {
        Lot lot = newLot();
        assertThat(lot.getState()).isEqualTo(LotState.OPEN);
        assertThat(lot.isOpen()).isTrue();
        assertThat(lot.getClosedAt()).isNull();

        Instant closedAt = Instant.parse("2026-07-21T10:00:00Z");
        lot.close(closedAt);

        assertThat(lot.getState()).isEqualTo(LotState.CLOSED);
        assertThat(lot.getClosedAt()).isEqualTo(closedAt);
        assertThatThrownBy(() -> lot.close(closedAt))
                .as("reopening would leave prices resting on costs that no longer exist")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    @DisplayName("A box can be finished short, and reopened if finished by mistake")
    void boxFinishesAndReopens() {
        Lot lot = newLot();
        Box box = boxes.save(new Box(lot, "BOX-SHORT"));

        box.finish(Instant.parse("2026-07-21T18:00:00Z"));
        assertThat(box.isFinished()).isTrue();

        box.reopen();
        assertThat(box.isFinished())
                .as("a box finished by mistake must not trap the operator")
                .isFalse();
    }
}
