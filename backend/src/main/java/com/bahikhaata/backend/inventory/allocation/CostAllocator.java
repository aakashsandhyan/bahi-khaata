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
package com.bahikhaata.backend.inventory.allocation;

import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Spreads what was paid for a lot across the products in it.
 *
 * <p>Stock is bought as a pallet for one sum, so a per-product cost is derived, never entered.
 * The lot amount stays the auditable figure and the line totals always reconcile back to it —
 * exactly, in paise, with no drift.
 *
 * <p>A pure function over plain values: no entities, no repositories, no Spring. The arithmetic
 * here decides every margin the business ever reports, so it is written to be read on its own.
 *
 * <p>How the apportioned remainder is divided is the one thing that varies, and it lives
 * behind {@link AllocationWeighting}. Everything else — freight, pinning, reconciliation,
 * unit cost — is common and is tested once.
 */
public final class CostAllocator {

    private final AllocationWeighting weighting;

    public CostAllocator(AllocationWeighting weighting) {
        this.weighting = Objects.requireNonNull(weighting, "weighting");
    }

    /**
     * Allocates a lot across its lines.
     *
     * @param amountPaid what was paid for the lot
     * @param freight what it cost to get here — part of landed cost, so it is allocated too
     * @param lines the delivery, in the order they should be returned
     * @throws IllegalArgumentException if the lot cannot be allocated, saying why
     */
    public Allocation allocate(Money amountPaid, Money freight, List<AllocationLine> lines) {
        Objects.requireNonNull(amountPaid, "amountPaid");
        Objects.requireNonNull(freight, "freight");
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("a lot must have at least one line");
        }
        if (amountPaid.isNegative() || freight.isNegative()) {
            throw new IllegalArgumentException("a lot amount cannot be negative");
        }
        for (AllocationLine line : lines) {
            if (line.sellableQuantity() == 0) {
                throw new IllegalArgumentException(
                        "line "
                                + line.reference()
                                + ": every unit arrived damaged, so it has no sellable quantity "
                                + "to carry a unit cost. Record the loss separately rather than "
                                + "as a received line.");
            }
        }

        Money total = amountPaid.plus(freight);

        Money pinnedTotal = Money.ZERO;
        List<AllocationLine> unpinned = new ArrayList<>();
        for (AllocationLine line : lines) {
            if (line.isPinned()) {
                pinnedTotal = pinnedTotal.plus(line.pinnedTotal());
            } else {
                unpinned.add(line);
            }
        }

        if (pinnedTotal.compareTo(total) > 0) {
            throw new IllegalArgumentException(
                    "pinned costs total "
                            + pinnedTotal
                            + ", which exceeds the lot amount of "
                            + total
                            + " by "
                            + pinnedTotal.minus(total));
        }

        // Pinned lines are removed from the pool, not adjusted afterwards: their total comes
        // off the lot amount first, and only what is left is apportioned. Overwriting an
        // allocated figure after the fact would leave the line totals no longer summing to
        // what was paid, which is the one property this whole scheme exists to preserve.
        Money remainder = total.minus(pinnedTotal);

        if (unpinned.isEmpty()) {
            if (!remainder.isZero()) {
                throw new IllegalArgumentException(
                        "every line is pinned, so the pinned costs must equal the lot amount "
                                + "exactly, but they fall short by "
                                + remainder);
            }
            return new Allocation(pinnedOnly(lines), AllocationMethod.FULLY_PINNED, total);
        }

        if (!remainder.isPositive()) {
            throw new IllegalArgumentException(
                    "pinned costs consume the entire lot amount, leaving nothing for the "
                            + unpinned.size()
                            + " unpinned line(s), which would make that stock free. Pin them at "
                            + "zero if they genuinely were.");
        }

        return new Allocation(
                apportion(lines, unpinned, remainder), weighting.method(), total);
    }

    private List<AllocatedLine> pinnedOnly(List<AllocationLine> lines) {
        List<AllocatedLine> allocated = new ArrayList<>(lines.size());
        for (AllocationLine line : lines) {
            allocated.add(pinned(line));
        }
        return allocated;
    }

    private List<AllocatedLine> apportion(
            List<AllocationLine> lines, List<AllocationLine> unpinned, Money remainder) {

        long[] weights = new long[unpinned.size()];
        long totalWeight = 0;
        for (int i = 0; i < unpinned.size(); i++) {
            long weight = weighting.weightOf(unpinned.get(i));
            if (weight <= 0) {
                throw new IllegalArgumentException(
                        "line "
                                + unpinned.get(i).reference()
                                + ": weighting produced "
                                + weight
                                + ", which would allocate it nothing and make its stock free");
            }
            weights[i] = weight;
            totalWeight = Math.addExact(totalWeight, weight);
        }

        // Floor division leaves a remainder of a few paise; it is assigned below rather than
        // lost. Multiply first, divide once — dividing first would compound rounding error
        // across every line.
        long[] shares = new long[unpinned.size()];
        long distributed = 0;
        for (int i = 0; i < unpinned.size(); i++) {
            shares[i] = Math.multiplyExact(remainder.paise(), weights[i]) / totalWeight;
            distributed = Math.addExact(distributed, shares[i]);
        }

        // The leftover goes to the largest share, where it is proportionally least
        // distorting. Ties break to the earliest line, so the same input always gives the
        // same output.
        long leftover = remainder.paise() - distributed;
        if (leftover > 0) {
            int largest = 0;
            for (int i = 1; i < shares.length; i++) {
                if (shares[i] > shares[largest]) {
                    largest = i;
                }
            }
            shares[largest] += leftover;
        }

        List<AllocatedLine> allocated = new ArrayList<>(lines.size());
        int next = 0;
        for (AllocationLine line : lines) {
            allocated.add(line.isPinned() ? pinned(line) : share(line, shares[next++]));
        }
        return allocated;
    }

    private AllocatedLine pinned(AllocationLine line) {
        // The pinned rate is what was paid per unit that *arrived*. Damaged units still cost
        // that, so the stored unit cost — the share over what can be sold — comes out above
        // the pinned rate whenever a line arrives damaged.
        Money lineTotal = line.pinnedTotal();
        return new AllocatedLine(
                line.reference(), lineTotal, unitCost(lineTotal, line), CostBasis.PINNED);
    }

    private AllocatedLine share(AllocationLine line, long sharePaise) {
        Money lineTotal = Money.ofPaise(sharePaise);
        return new AllocatedLine(
                line.reference(), lineTotal, unitCost(lineTotal, line), CostBasis.ALLOCATED);
    }

    /**
     * The line's share divided by what can actually be sold.
     *
     * <p>Damaged units are excluded from the divisor, so their cost is absorbed by the units
     * that can earn — the full lot was paid for regardless of how much of it sells.
     *
     * <p>Floor division, so multiplying this back out never exceeds the line total. The
     * fraction lost here is exactly why the total is stored alongside and is the figure that
     * reconciles.
     */
    private Money unitCost(Money lineTotal, AllocationLine line) {
        return Money.ofPaise(lineTotal.paise() / line.sellableQuantity());
    }
}
