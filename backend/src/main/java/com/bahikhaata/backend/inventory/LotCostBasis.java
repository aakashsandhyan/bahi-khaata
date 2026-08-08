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

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.contracts.CostAnchor;
import com.bahikhaata.contracts.CostBasisStrategy;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.MrpRateBand;
import com.bahikhaata.contracts.MultiplierBase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Derives a lot's per-unit cost from its declared cost basis.
 *
 * <p>Pure and stateless: everything it needs — the strategy, its params, the resolved bands, the
 * anchor value, the manifest's stated value — is passed in. Never guesses: a strategy whose
 * required input is not yet known (an anchor not yet observed, an MRP outside every band, a base
 * that is null) returns null so the caller leaves the batch uncosted rather than pin a fabricated
 * figure. All ratio math runs in {@link BigDecimal}, rounded half-up to the paise, matching the
 * rounding the existing manifest-rate pin already uses — never {@code float}/{@code double}
 * (banned in this module, see {@code FloatingPointPolicyTest}).
 */
@Component
public class LotCostBasis {

    private static final BigDecimal BASIS_POINTS = BigDecimal.valueOf(10_000);
    private static final BigDecimal MILLI_UNITS = BigDecimal.valueOf(1_000);

    /**
     * The per-unit cost this lot's basis derives, or null while the input it needs is not yet
     * known. {@code anchorValue} is the batch's MRP or the product's ASP, whichever this lot's
     * anchor names — see {@link #anchorValue}; {@code statedValuePaise} is the manifest line's
     * stated value, used only by a {@code MULTIPLIER} basis whose base is
     * {@link MultiplierBase#STATED_VALUE}.
     */
    public Money unitCost(Lot lot, List<LotMrpRateBand> bands, Money anchorValue, Money statedValue) {
        CostBasisStrategy strategy = lot.getCostBasisStrategy();
        if (strategy == null) {
            return null;
        }
        return switch (strategy) {
            case FLAT_PER_UNIT -> lot.getFlatUnitCost();
            case PERCENT_OF_ANCHOR -> percentOf(anchorValue, lot.getPercentBp());
            case MRP_RATE_RANGE -> bandCost(bands, anchorValue);
            case MULTIPLIER -> multiplierCost(lot, anchorValue, statedValue);
        };
    }

    private Money percentOf(Money anchor, Long percentBp) {
        if (anchor == null || percentBp == null) {
            return null;
        }
        BigDecimal result = BigDecimal.valueOf(anchor.paise())
                .multiply(BigDecimal.valueOf(percentBp))
                .divide(BASIS_POINTS, 0, RoundingMode.HALF_UP);
        return Money.ofPaise(result.longValueExact());
    }

    /** The band an MRP falls in — min inclusive, max exclusive, an open-topped final band. */
    private Money bandCost(List<LotMrpRateBand> bands, Money mrp) {
        if (mrp == null || bands == null) {
            return null;
        }
        long value = mrp.paise();
        for (LotMrpRateBand band : bands) {
            if (value >= band.getMinMrp().paise()
                    && (band.getMaxMrp() == null || value < band.getMaxMrp().paise())) {
                return band.getCost();
            }
        }
        return null;
    }

    private Money multiplierCost(Lot lot, Money anchorValue, Money statedValue) {
        Long multiplierMilli = lot.getMultiplierMilli();
        MultiplierBase base = lot.getMultiplierBase();
        if (multiplierMilli == null || base == null) {
            return null;
        }
        Money baseValue = switch (base) {
            case ENTERED_UNIT_COST -> lot.getFlatUnitCost();
            case ANCHOR -> anchorValue;
            case STATED_VALUE -> statedValue;
        };
        if (baseValue == null) {
            return null;
        }
        BigDecimal result = BigDecimal.valueOf(baseValue.paise())
                .multiply(BigDecimal.valueOf(multiplierMilli))
                .divide(MILLI_UNITS, 0, RoundingMode.HALF_UP);
        return Money.ofPaise(result.longValueExact());
    }

    /**
     * The anchor value a batch reads under this lot's declared anchor — the batch's recorded MRP,
     * or the product's observed online price — or null when the lot needs no anchor or the
     * anchor itself is not yet known.
     */
    public Money anchorValue(Lot lot, Batch batch, Product product) {
        CostAnchor anchor = lot.getCostAnchor();
        if (anchor == null) {
            return null;
        }
        return switch (anchor) {
            case MRP -> batch.getMrp();
            case ASP -> product.getOnlinePrice();
        };
    }

    /**
     * Validates a candidate cost basis against what its strategy needs, naming what is missing.
     * Called before anything is persisted, so an invalid request never reaches the database. A
     * null {@code strategy} — no declared basis — is always valid; there is nothing to check.
     */
    public void requireValidBasis(
            CostBasisStrategy strategy,
            CostAnchor anchor,
            Money flatUnitCost,
            Long percentBp,
            Long multiplierMilli,
            MultiplierBase multiplierBase,
            List<MrpRateBand> bands) {
        if (strategy == null) {
            return;
        }
        switch (strategy) {
            case FLAT_PER_UNIT -> requirePresent(flatUnitCost, "flatUnitCostPaise", strategy);
            case PERCENT_OF_ANCHOR -> {
                requirePresent(percentBp, "percentBp", strategy);
                requirePresent(anchor, "costAnchor", strategy);
            }
            case MRP_RATE_RANGE -> {
                if (bands == null || bands.isEmpty()) {
                    throw new IllegalArgumentException(
                            "MRP_RATE_RANGE needs at least one rate band (rateBands)");
                }
                if (anchor != CostAnchor.MRP) {
                    throw new IllegalArgumentException(
                            "MRP_RATE_RANGE anchors to MRP; costAnchor was " + anchor);
                }
                requireNonOverlappingAscending(bands);
            }
            case MULTIPLIER -> {
                requirePresent(multiplierMilli, "multiplierMilli", strategy);
                requirePresent(multiplierBase, "multiplierBase", strategy);
                if (multiplierBase == MultiplierBase.ANCHOR) {
                    requirePresent(anchor, "costAnchor", strategy);
                }
                if (multiplierBase == MultiplierBase.ENTERED_UNIT_COST) {
                    requirePresent(flatUnitCost, "flatUnitCostPaise", strategy);
                }
            }
        }
    }

    private void requirePresent(Object value, String field, CostBasisStrategy strategy) {
        if (value == null) {
            throw new IllegalArgumentException(strategy + " needs " + field);
        }
    }

    /**
     * A rate card's bands must not overlap and must cover ascending, disjoint MRP ranges — sorted
     * by their minimum, each one's minimum must sit at or above the previous one's maximum, and
     * only the last may be open-topped.
     */
    private void requireNonOverlappingAscending(List<MrpRateBand> bands) {
        List<MrpRateBand> sorted =
                bands.stream().sorted(Comparator.comparingLong(MrpRateBand::minMrpPaise)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            MrpRateBand previous = sorted.get(i - 1);
            MrpRateBand current = sorted.get(i);
            if (previous.maxMrpPaise() == null) {
                throw new IllegalArgumentException(
                        "an open-topped rate band must be the last one; found another band after "
                                + previous);
            }
            if (current.minMrpPaise() < previous.maxMrpPaise()) {
                throw new IllegalArgumentException(
                        "rate bands overlap: " + previous + " and " + current);
            }
        }
    }
}
