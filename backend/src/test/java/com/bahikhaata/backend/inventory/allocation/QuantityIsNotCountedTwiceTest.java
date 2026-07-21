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

import com.bahikhaata.contracts.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Two units of a thing cost twice what one does — no more.
 *
 * <p>The weighting strategy multiplies by quantity itself, so a caller that hands it a line
 * total rather than a per-unit value applies quantity twice. The weight becomes value ×
 * quantity², multi-unit lines take an inflated share, and single-unit lines are squeezed to
 * make room for them.
 *
 * <p>This went unnoticed through a full import of 3,583 units because the lot totals were
 * still exactly right. They had to be: the shares are shares, and shares of the amount paid
 * sum to the amount paid however wrongly they are split. The reconciliation property test —
 * two thousand random lots, every one balancing to the paise — could not see it, and neither
 * could anyone reading the totals. Only the split was wrong.
 *
 * <p>So these assertions deliberately never look at the total. They compare lines with each
 * other, which is the only place the fault shows.
 */
class QuantityIsNotCountedTwiceTest {

    private final CostAllocator allocator = new CostAllocator(new RelativeMrpWeighting());

    private static AllocationLine line(String reference, long quantity, long unitValuePaise) {
        return new AllocationLine(reference, quantity, 0, Money.ofPaise(unitValuePaise), null);
    }

    @Test
    void lineOfEqualUnitValueGetsEqualUnitCostWhateverItsQuantity() {
        // Same value per unit; only the count differs. Whatever each unit ends up costing,
        // it must be the same figure in all three.
        Allocation allocation =
                allocator.allocate(
                        Money.ofPaise(120_000),
                        Money.ZERO,
                        List.of(
                                line("one", 1, 10_000),
                                line("two", 2, 10_000),
                                line("nine", 9, 10_000)));

        Money unitCost = allocation.lines().get(0).allocatedUnitCost();
        assertThat(allocation.lines().get(1).allocatedUnitCost())
                .as("two units of the same thing cost twice one, so each unit costs the same")
                .isEqualTo(unitCost);
        assertThat(allocation.lines().get(2).allocatedUnitCost())
                .as("and nine units likewise; quantity must not compound")
                .isEqualTo(unitCost);
    }

    @Test
    void unitCostTracksUnitValueAndNotQuantity() {
        // A single expensive unit against many cheap ones. The dear unit must cost ten times
        // the cheap one, regardless of how many cheap ones there are.
        Allocation allocation =
                allocator.allocate(
                        Money.ofPaise(1_100_000),
                        Money.ZERO,
                        List.of(line("dear", 1, 100_000), line("cheap", 100, 10_000)));

        long dear = allocation.lines().get(0).allocatedUnitCost().paise();
        long cheap = allocation.lines().get(1).allocatedUnitCost().paise();

        assertThat(dear)
                .as("worth ten times as much per unit, so it must cost ten times as much")
                .isEqualTo(cheap * 10);
    }

    @Test
    void aUniformFactorAcrossTheLotReachesEveryLine() {
        // The real shape of a liquidation manifest: pay a fixed fraction of what the goods
        // fetched online. That fraction must land on every line, not just the average.
        Allocation allocation =
                allocator.allocate(
                        Money.ofPaise(250_000), // a quarter of 1,000,000 of stated value
                        Money.ZERO,
                        List.of(
                                line("a", 1, 400_000),
                                line("b", 2, 200_000),
                                line("c", 4, 50_000)));

        assertThat(allocation.lines())
                .allSatisfy(
                        allocated ->
                                assertThat(allocated.allocatedTotal().paise() * 4)
                                        .as(
                                                "%s: paying a quarter of stated value means every"
                                                        + " line costs a quarter of its own value",
                                                allocated.reference())
                                        .isEqualTo(statedValueOf(allocated.reference())));
    }

    private static long statedValueOf(String reference) {
        return switch (reference) {
            case "a" -> 400_000L;
            case "b" -> 400_000L;
            case "c" -> 200_000L;
            default -> throw new IllegalArgumentException(reference);
        };
    }
}
