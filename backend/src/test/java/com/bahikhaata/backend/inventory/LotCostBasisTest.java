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

import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.CostAnchor;
import com.bahikhaata.contracts.CostBasisStrategy;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.MrpRateBand;
import com.bahikhaata.contracts.MultiplierBase;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The resolver's formulas, in isolation from persistence: given a lot's declared basis and the
 * inputs it needs, does it derive the right cost, and does it say null — never a guess — when
 * one of those inputs is not yet known. Rounding is checked against the same half-up-to-the-paise
 * rule the legacy manifest-rate pin already uses.
 */
class LotCostBasisTest {

    private final LotCostBasis resolver = new LotCostBasis();

    private Lot lot() {
        return new Lot(
                "Test Supplier",
                LocalDate.of(2026, 8, 1),
                Money.ofRupees(1_000),
                Money.ZERO,
                AllocationMethod.RELATIVE_MRP);
    }

    // --- no declared basis ---

    @Test
    void aLotWithNoStrategyResolvesNothing() {
        Lot lot = lot();
        assertThat(resolver.unitCost(lot, List.of(), Money.ofRupees(500), Money.ofRupees(100))).isNull();
    }

    // --- FLAT_PER_UNIT ---

    @Test
    void flatPerUnitIsAlwaysKnownRegardlessOfAnchor() {
        Lot lot = lot();
        lot.setCostBasisStrategy(CostBasisStrategy.FLAT_PER_UNIT);
        lot.setFlatUnitCost(Money.ofPaise(12_345));

        assertThat(resolver.unitCost(lot, List.of(), null, null)).isEqualTo(Money.ofPaise(12_345));
    }

    // --- PERCENT_OF_ANCHOR ---

    @Test
    void percentOfAnchorComputesAndRoundsHalfUp() {
        Lot lot = lot();
        lot.setCostBasisStrategy(CostBasisStrategy.PERCENT_OF_ANCHOR);
        lot.setCostAnchor(CostAnchor.MRP);
        lot.setPercentBp(3_000L); // 30%

        // 30% of 999 paise = 299.7, rounds up to 300.
        Money resolved = resolver.unitCost(lot, List.of(), Money.ofPaise(999), null);

        assertThat(resolved).isEqualTo(Money.ofPaise(300));
    }

    @Test
    void percentOfAnchorIsNullWhenTheAnchorIsNotYetKnown() {
        Lot lot = lot();
        lot.setCostBasisStrategy(CostBasisStrategy.PERCENT_OF_ANCHOR);
        lot.setCostAnchor(CostAnchor.MRP);
        lot.setPercentBp(3_000L);

        assertThat(resolver.unitCost(lot, List.of(), null, null)).isNull();
    }

    // --- MRP_RATE_RANGE ---

    @Test
    void rateRangeBandsAreMinInclusiveMaxExclusiveWithAnOpenTop() {
        Lot lot = lot();
        lot.setCostBasisStrategy(CostBasisStrategy.MRP_RATE_RANGE);
        lot.setCostAnchor(CostAnchor.MRP);
        List<LotMrpRateBand> bands = List.of(
                new LotMrpRateBand(lot, Money.ofPaise(0), Money.ofPaise(500_00), Money.ofPaise(50_00)),
                new LotMrpRateBand(lot, Money.ofPaise(500_00), Money.ofPaise(1_000_00), Money.ofPaise(150_00)),
                new LotMrpRateBand(lot, Money.ofPaise(1_000_00), null, Money.ofPaise(400_00)));

        // The lower band's minimum is inclusive.
        assertThat(resolver.unitCost(lot, bands, Money.ofPaise(0), null)).isEqualTo(Money.ofPaise(50_00));
        // The lower band's maximum is exclusive — the boundary value belongs to the next band up.
        assertThat(resolver.unitCost(lot, bands, Money.ofPaise(500_00), null)).isEqualTo(Money.ofPaise(150_00));
        assertThat(resolver.unitCost(lot, bands, Money.ofPaise(499_99), null)).isEqualTo(Money.ofPaise(50_00));
        // The open-topped final band catches everything at or above its minimum.
        assertThat(resolver.unitCost(lot, bands, Money.ofPaise(50_000_00), null))
                .isEqualTo(Money.ofPaise(400_00));
    }

    @Test
    void anMrpOutsideEveryBandIsLeftUncostedRatherThanGuessed() {
        Lot lot = lot();
        lot.setCostBasisStrategy(CostBasisStrategy.MRP_RATE_RANGE);
        lot.setCostAnchor(CostAnchor.MRP);
        List<LotMrpRateBand> bands =
                List.of(new LotMrpRateBand(lot, Money.ofPaise(500_00), Money.ofPaise(1_000_00), Money.ofPaise(150_00)));

        assertThat(resolver.unitCost(lot, bands, Money.ofPaise(100_00), null)).isNull();
    }

    // --- MULTIPLIER ---

    @Test
    void multiplierOnAnEnteredCost() {
        Lot lot = lot();
        lot.setCostBasisStrategy(CostBasisStrategy.MULTIPLIER);
        lot.setMultiplierMilli(1_250L); // 1.25x
        lot.setMultiplierBase(MultiplierBase.ENTERED_UNIT_COST);
        lot.setFlatUnitCost(Money.ofPaise(1_000));

        assertThat(resolver.unitCost(lot, List.of(), null, null)).isEqualTo(Money.ofPaise(1_250));
    }

    @Test
    void multiplierOnTheAnchor() {
        Lot lot = lot();
        lot.setCostBasisStrategy(CostBasisStrategy.MULTIPLIER);
        lot.setCostAnchor(CostAnchor.ASP);
        lot.setMultiplierMilli(500L); // 0.5x
        lot.setMultiplierBase(MultiplierBase.ANCHOR);

        assertThat(resolver.unitCost(lot, List.of(), Money.ofPaise(2_000), null)).isEqualTo(Money.ofPaise(1_000));
    }

    @Test
    void multiplierOnTheManifestStatedValue() {
        Lot lot = lot();
        lot.setCostBasisStrategy(CostBasisStrategy.MULTIPLIER);
        lot.setMultiplierMilli(1_100L); // 1.1x
        lot.setMultiplierBase(MultiplierBase.STATED_VALUE);

        assertThat(resolver.unitCost(lot, List.of(), null, Money.ofPaise(10_000)))
                .isEqualTo(Money.ofPaise(11_000));
    }

    @Test
    void multiplierIsNullWhenItsChosenBaseIsNotYetKnown() {
        Lot lot = lot();
        lot.setCostBasisStrategy(CostBasisStrategy.MULTIPLIER);
        lot.setMultiplierMilli(1_100L);
        lot.setMultiplierBase(MultiplierBase.STATED_VALUE);

        assertThat(resolver.unitCost(lot, List.of(), null, null)).isNull();
    }

    // --- requireValidBasis ---

    @Test
    void flatPerUnitNeedsAFlatCost() {
        assertThatThrownBy(() ->
                        resolver.requireValidBasis(
                                CostBasisStrategy.FLAT_PER_UNIT, null, null, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("flatUnitCostPaise");
    }

    @Test
    void percentOfAnchorNeedsAPercentAndAnAnchor() {
        assertThatThrownBy(() ->
                        resolver.requireValidBasis(
                                CostBasisStrategy.PERCENT_OF_ANCHOR, null, null, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percentBp");

        assertThatThrownBy(() ->
                        resolver.requireValidBasis(
                                CostBasisStrategy.PERCENT_OF_ANCHOR, null, null, 3_000L, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("costAnchor");

        // Both present: no exception.
        resolver.requireValidBasis(
                CostBasisStrategy.PERCENT_OF_ANCHOR, CostAnchor.MRP, null, 3_000L, null, null, List.of());
    }

    @Test
    void rateRangeNeedsAtLeastOneBandAndAnMrpAnchor() {
        assertThatThrownBy(() ->
                        resolver.requireValidBasis(
                                CostBasisStrategy.MRP_RATE_RANGE, CostAnchor.MRP, null, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate band");

        List<MrpRateBand> bands = List.of(new MrpRateBand(0, 500_00L, 50_00));
        assertThatThrownBy(() ->
                        resolver.requireValidBasis(
                                CostBasisStrategy.MRP_RATE_RANGE, CostAnchor.ASP, null, null, null, null, bands))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MRP");
    }

    @Test
    void rateRangeRejectsOverlappingBands() {
        List<MrpRateBand> overlapping =
                List.of(new MrpRateBand(0, 600_00L, 50_00), new MrpRateBand(500_00, 1_000_00L, 150_00));

        assertThatThrownBy(() ->
                        resolver.requireValidBasis(
                                CostBasisStrategy.MRP_RATE_RANGE, CostAnchor.MRP, null, null, null, null, overlapping))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void rateRangeRejectsAnOpenToppedBandThatIsNotLast() {
        List<MrpRateBand> badOrder =
                List.of(new MrpRateBand(0, null, 50_00), new MrpRateBand(500_00, 1_000_00L, 150_00));

        assertThatThrownBy(() ->
                        resolver.requireValidBasis(
                                CostBasisStrategy.MRP_RATE_RANGE, CostAnchor.MRP, null, null, null, null, badOrder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("open-topped");
    }

    @Test
    void multiplierNeedsAFactorAndABaseAndAnEnteredCostWhenTheBaseIsEntered() {
        assertThatThrownBy(() ->
                        resolver.requireValidBasis(
                                CostBasisStrategy.MULTIPLIER, null, null, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiplierMilli");

        assertThatThrownBy(() ->
                        resolver.requireValidBasis(
                                CostBasisStrategy.MULTIPLIER, null, null, null, 1_250L, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiplierBase");

        assertThatThrownBy(() ->
                        resolver.requireValidBasis(
                                CostBasisStrategy.MULTIPLIER, null, null, null, 1_250L,
                                MultiplierBase.ENTERED_UNIT_COST, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("flatUnitCostPaise");

        // A valid multiplier on an anchor base: no exception.
        resolver.requireValidBasis(
                CostBasisStrategy.MULTIPLIER, CostAnchor.ASP, null, null, 500L, MultiplierBase.ANCHOR, List.of());
    }

    @Test
    void aLotWithNoDeclaredBasisIsAlwaysValid() {
        resolver.requireValidBasis(null, null, null, null, null, null, List.of());
    }
}
