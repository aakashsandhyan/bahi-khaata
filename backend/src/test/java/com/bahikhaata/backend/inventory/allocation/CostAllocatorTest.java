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
package com.bahikhaata.backend.inventory.allocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic that decides every margin the business reports.
 *
 * <p>No Spring, no database: the allocator is a pure function, so these read as arithmetic.
 */
class CostAllocatorTest {

    private final CostAllocator allocator = new CostAllocator(new RelativeMrpWeighting());

    private static AllocationLine line(String ref, long quantity, long mrpRupees) {
        return new AllocationLine(ref, quantity, 0, Money.ofRupees(mrpRupees), null);
    }

    private static AllocationLine damaged(String ref, long quantity, long damaged, long mrpRupees) {
        return new AllocationLine(ref, quantity, damaged, Money.ofRupees(mrpRupees), null);
    }

    private static AllocationLine pinned(
            String ref, long quantity, long mrpRupees, long pinnedRupees) {
        return new AllocationLine(
                ref, quantity, 0, Money.ofRupees(mrpRupees), Money.ofRupees(pinnedRupees));
    }

    private static AllocatedLine find(Allocation allocation, String reference) {
        return allocation.lines().stream()
                .filter(l -> l.reference().equals(reference))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("Proportional allocation")
    class Proportional {

        @Test
        @DisplayName("A mixed pallet costs each line in proportion to its retail value")
        void mixedPalletIsProportional() {
            // ₹50,000 pallet: 100 bottles at ₹300, 50 kettles at ₹800, 200 keychains at ₹50.
            // Retail value 30,000 + 40,000 + 10,000 = ₹80,000.
            Allocation allocation =
                    allocator.allocate(
                            Money.ofRupees(50_000),
                            Money.ZERO,
                            List.of(
                                    line("bottle", 100, 300),
                                    line("kettle", 50, 800),
                                    line("keychain", 200, 50)));

            // 30/80 of ₹50,000 = ₹18,750 over 100 bottles = ₹187.50 each.
            assertThat(find(allocation, "bottle").allocatedUnitCost())
                    .isEqualTo(Money.ofPaise(18_750));
            // 40/80 = ₹25,000 over 50 kettles = ₹500 each.
            assertThat(find(allocation, "kettle").allocatedUnitCost())
                    .isEqualTo(Money.ofRupees(500));
            // 10/80 = ₹6,250 over 200 keychains = ₹31.25 each.
            assertThat(find(allocation, "keychain").allocatedUnitCost())
                    .isEqualTo(Money.ofPaise(3_125));
        }

        @Test
        @DisplayName("A low-value item is never costed above what it retails for")
        void lowValueItemStaysBelowItsRetailPrice() {
            // The failure an equal split per unit would produce: ₹50,000 over 350 units is
            // ₹142.86 each, pricing a ₹50 keychain at nearly three times its retail price and
            // reporting a catastrophic loss on every one sold.
            Allocation allocation =
                    allocator.allocate(
                            Money.ofRupees(50_000),
                            Money.ZERO,
                            List.of(
                                    line("bottle", 100, 300),
                                    line("kettle", 50, 800),
                                    line("keychain", 200, 50)));

            AllocatedLine keychain = find(allocation, "keychain");
            assertThat(keychain.allocatedUnitCost()).isLessThan(Money.ofRupees(50));
        }

        @Test
        @DisplayName("Freight is landed cost and is allocated with the amount paid")
        void freightIsAllocated() {
            Allocation withoutFreight =
                    allocator.allocate(Money.ofRupees(10_000), Money.ZERO, List.of(line("a", 100, 100)));
            Allocation withFreight =
                    allocator.allocate(
                            Money.ofRupees(10_000), Money.ofRupees(2_000), List.of(line("a", 100, 100)));

            assertThat(withoutFreight.totalAllocated()).isEqualTo(Money.ofRupees(10_000));
            // Leaving freight out would understate every unit.
            assertThat(withFreight.totalAllocated()).isEqualTo(Money.ofRupees(12_000));
            assertThat(find(withFreight, "a").allocatedUnitCost()).isEqualTo(Money.ofRupees(120));
        }
    }

    @Nested
    @DisplayName("Pinned lines")
    class Pinning {

        @Test
        @DisplayName("Pinning one line changes what the other lines receive")
        void pinningChangesTheOtherLines() {
            List<AllocationLine> unpinnedLot =
                    List.of(line("bottle", 100, 300), line("kettle", 50, 800));
            Money paid = Money.ofRupees(50_000);

            Money bottleWithoutPin =
                    find(allocator.allocate(paid, Money.ZERO, unpinnedLot), "bottle")
                            .allocatedTotal();

            // The supplier itemised the kettles at ₹450 — ₹22,500 of the ₹50,000.
            Allocation pinnedLot =
                    allocator.allocate(
                            paid,
                            Money.ZERO,
                            List.of(line("bottle", 100, 300), pinned("kettle", 50, 800, 450)));

            // This is the distinction between removing a line from the pool and overwriting
            // its result afterwards: the bottles' share must change too.
            assertThat(find(pinnedLot, "bottle").allocatedTotal())
                    .isNotEqualTo(bottleWithoutPin)
                    .isEqualTo(Money.ofRupees(27_500));
            assertThat(find(pinnedLot, "kettle").allocatedTotal())
                    .isEqualTo(Money.ofRupees(22_500));
        }

        @Test
        @DisplayName("A pinned cost is charged on everything that arrived, damaged included")
        void pinnedCostCoversDamagedUnits() {
            // 100 kettles pinned at ₹450, two damaged: ₹45,000 was paid for the hundred.
            Allocation allocation =
                    allocator.allocate(
                            Money.ofRupees(50_000),
                            Money.ZERO,
                            List.of(
                                    new AllocationLine(
                                            "kettle", 100, 2, Money.ofRupees(800), Money.ofRupees(450)),
                                    line("bottle", 100, 300)));

            AllocatedLine kettle = find(allocation, "kettle");
            assertThat(kettle.allocatedTotal()).isEqualTo(Money.ofRupees(45_000));
            // Spread over the 98 that can be sold, so the stored cost lands above the pinned
            // rate — the damaged two were paid for and must be carried by the rest.
            assertThat(kettle.allocatedUnitCost()).isEqualTo(Money.ofPaise(45_918));
            assertThat(kettle.allocatedUnitCost()).isGreaterThan(Money.ofRupees(450));
        }

        @Test
        @DisplayName("Pinned costs exceeding the lot amount are refused, reporting the excess")
        void pinnedTotalCannotExceedTheLot() {
            assertThatThrownBy(
                            () ->
                                    allocator.allocate(
                                            Money.ofRupees(10_000),
                                            Money.ZERO,
                                            List.of(
                                                    pinned("kettle", 50, 800, 450),
                                                    line("bottle", 10, 300))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds the lot amount")
                    // The operator needs the size of the discrepancy, not just its existence.
                    .hasMessageContaining("₹12,500.00");
        }

        @Test
        @DisplayName("A fully pinned lot is accepted only when it reconciles exactly")
        void fullyPinnedMustReconcile() {
            List<AllocationLine> everyLinePinned =
                    List.of(pinned("kettle", 50, 800, 450), pinned("bottle", 100, 300, 275));

            // 50 × 450 + 100 × 275 = ₹50,000 exactly.
            Allocation exact =
                    allocator.allocate(Money.ofRupees(50_000), Money.ZERO, everyLinePinned);
            assertThat(exact.method()).isEqualTo(AllocationMethod.FULLY_PINNED);
            assertThat(exact.totalAllocated()).isEqualTo(Money.ofRupees(50_000));

            // A rupee short is a data-entry error, not something to silently absorb.
            assertThatThrownBy(
                            () ->
                                    allocator.allocate(
                                            Money.ofRupees(50_001), Money.ZERO, everyLinePinned))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fall short");
        }

        @Test
        @DisplayName("Pinned lines consuming the whole lot leave unpinned stock free, so refuse")
        void pinnedLinesCannotStarveUnpinnedOnes() {
            assertThatThrownBy(
                            () ->
                                    allocator.allocate(
                                            Money.ofRupees(22_500),
                                            Money.ZERO,
                                            List.of(
                                                    pinned("kettle", 50, 800, 450),
                                                    line("bottle", 100, 300))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("would make that stock free");
        }
    }

    @Nested
    @DisplayName("Damaged units")
    class Damage {

        @Test
        @DisplayName("Damaged units are paid for, and their cost is absorbed by the sellable ones")
        void damagedCostIsAbsorbed() {
            Allocation allocation =
                    allocator.allocate(
                            Money.ofRupees(10_000),
                            Money.ZERO,
                            List.of(damaged("bottle", 100, 20, 100)));

            // The whole ₹10,000 is still allocated — the pallet was paid for regardless.
            assertThat(find(allocation, "bottle").allocatedTotal())
                    .isEqualTo(Money.ofRupees(10_000));
            // Spread over the 80 that can be sold: ₹125, not the ₹100 a naive divisor gives.
            assertThat(find(allocation, "bottle").allocatedUnitCost())
                    .isEqualTo(Money.ofRupees(125));
        }

        @Test
        @DisplayName("A line where everything arrived damaged is refused, not silently costed")
        void fullyDamagedLineIsRefused() {
            assertThatThrownBy(
                            () ->
                                    allocator.allocate(
                                            Money.ofRupees(10_000),
                                            Money.ZERO,
                                            List.of(damaged("write-off", 10, 10, 100))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no sellable quantity");
        }
    }

    @Nested
    @DisplayName("Exact reconciliation")
    class Reconciliation {

        @Test
        @DisplayName("A remainder that does not divide evenly is assigned, not lost")
        void remainderIsAssigned() {
            // ₹1.00 across three equal lines: 33 + 33 + 33 leaves a paise over.
            Allocation allocation =
                    allocator.allocate(
                            Money.ofPaise(100),
                            Money.ZERO,
                            List.of(line("a", 1, 100), line("b", 1, 100), line("c", 1, 100)));

            assertThat(totalOf(allocation)).isEqualTo(Money.ofPaise(100));
            assertThat(allocation.lines())
                    .extracting(AllocatedLine::allocatedTotal)
                    // The stray paise lands on one line rather than vanishing.
                    .containsExactly(Money.ofPaise(34), Money.ofPaise(33), Money.ofPaise(33));
        }

        @Test
        @DisplayName("Line totals always sum to the lot amount, across many random lots")
        void alwaysReconciles() {
            // A worked example or two would not find the combinations where floor division
            // leaves a remainder; this generates them. Fixed seed, so a failure reproduces.
            Random random = new Random(20260720L);

            for (int lot = 0; lot < 2_000; lot++) {
                long amountPaise = 1 + random.nextInt(50_000_00);
                long freightPaise = random.nextInt(5_000_00);

                int lineCount = 1 + random.nextInt(8);
                List<AllocationLine> lines = new ArrayList<>(lineCount);
                for (int i = 0; i < lineCount; i++) {
                    long quantity = 1 + random.nextInt(500);
                    long damagedUnits = random.nextInt((int) Math.min(quantity, 10));
                    lines.add(
                            new AllocationLine(
                                    "line-" + i,
                                    quantity,
                                    damagedUnits,
                                    Money.ofPaise(1 + random.nextInt(100_000)),
                                    null));
                }

                Allocation allocation =
                        allocator.allocate(
                                Money.ofPaise(amountPaise), Money.ofPaise(freightPaise), lines);

                assertThat(totalOf(allocation))
                        .as("lot %d of %s + %s freight across %d lines",
                                lot,
                                Money.ofPaise(amountPaise),
                                Money.ofPaise(freightPaise),
                                lineCount)
                        .isEqualTo(Money.ofPaise(amountPaise + freightPaise));
            }
        }

        @Test
        @DisplayName("Unit cost multiplied out never exceeds the line total")
        void unitCostNeverOverstatesTheLine() {
            // Unit cost floors, so the stored total is the authoritative figure and the unit
            // cost can only ever understate it. Overstating would let cost of goods sold
            // exceed what was actually paid.
            Random random = new Random(20260721L);

            for (int lot = 0; lot < 500; lot++) {
                long quantity = 1 + random.nextInt(97);
                Allocation allocation =
                        allocator.allocate(
                                Money.ofPaise(1 + random.nextInt(1_000_000)),
                                Money.ZERO,
                                List.of(line("only", quantity, 1 + random.nextInt(1000))));

                AllocatedLine only = find(allocation, "only");
                assertThat(only.allocatedUnitCost().times(quantity))
                        .isLessThanOrEqualTo(only.allocatedTotal());
            }
        }

        private Money totalOf(Allocation allocation) {
            return allocation.lines().stream()
                    .map(AllocatedLine::allocatedTotal)
                    .reduce(Money.ZERO, Money::plus);
        }
    }

    @Nested
    @DisplayName("Provenance")
    class Provenance {

        @Test
        @DisplayName("Each line records how its cost was arrived at")
        void costBasisIsRecorded() {
            Allocation allocation =
                    allocator.allocate(
                            Money.ofRupees(50_000),
                            Money.ZERO,
                            List.of(line("bottle", 100, 300), pinned("kettle", 50, 800, 450)));

            assertThat(find(allocation, "bottle").basis()).isEqualTo(CostBasis.ALLOCATED);
            assertThat(find(allocation, "kettle").basis()).isEqualTo(CostBasis.PINNED);
        }

        @Test
        @DisplayName("The lot records the method that produced its figures")
        void methodIsRecorded() {
            assertThat(
                            allocator
                                    .allocate(
                                            Money.ofRupees(50_000),
                                            Money.ZERO,
                                            List.of(line("bottle", 100, 300)))
                                    .method())
                    .isEqualTo(AllocationMethod.RELATIVE_MRP);
        }

        @Test
        @DisplayName("Lines come back in the order they were supplied")
        void orderIsPreserved() {
            Allocation allocation =
                    allocator.allocate(
                            Money.ofRupees(50_000),
                            Money.ZERO,
                            List.of(
                                    line("first", 10, 100),
                                    pinned("second", 10, 100, 50),
                                    line("third", 10, 100)));

            assertThat(allocation.lines())
                    .extracting(AllocatedLine::reference)
                    .containsExactly("first", "second", "third");
        }
    }
}
