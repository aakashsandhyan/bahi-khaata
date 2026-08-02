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
package com.bahikhaata.backend.pricing;

import com.bahikhaata.contracts.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Gross margin arithmetic.
 *
 * <p>Gross margin is (price − cost) ÷ <strong>price</strong>. Markup is (price − cost) ÷
 * <strong>cost</strong>, and the two are not the same number: an item bought at ₹60 and sold at
 * ₹100 carries a 40% margin and a 67% markup. Confusing them is a standard retail arithmetic
 * error, and here it would make the review threshold fire at roughly the wrong sensitivity —
 * so the method is named for what it computes and the distinction is tested.
 *
 * <p>Percentages are the one place a decimal is unavoidable: a margin is a ratio, not an
 * amount. It is computed from exact paise and returned as a scaled {@link BigDecimal}, never a
 * {@code double}, so the money itself stays integer throughout.
 */
public final class Margins {

    /** Two decimal places: enough to compare against a threshold, not so much it reads as noise. */
    private static final int SCALE = 2;

    private Margins() {}

    /**
     * Gross margin as a percentage of the selling price.
     *
     * @throws IllegalArgumentException if the price is not positive — margin on a zero or
     *     absent price is undefined, and returning zero would read as "breaking even"
     */
    public static BigDecimal grossMarginPercent(Money sellingPrice, Money unitCost) {
        if (sellingPrice == null || !sellingPrice.isPositive()) {
            throw new IllegalArgumentException(
                    "gross margin is undefined without a positive selling price");
        }

        BigDecimal price = BigDecimal.valueOf(sellingPrice.paise());
        BigDecimal cost = BigDecimal.valueOf(unitCost.paise());

        return price.subtract(cost)
                .multiply(BigDecimal.valueOf(100))
                .divide(price, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The selling price that would achieve a target margin over a cost.
     *
     * <p>From margin = (price − cost) ÷ price, price = cost ÷ (1 − margin). Rounded up to the
     * paise so the achieved margin meets the target rather than falling a hair short.
     *
     * @throws IllegalArgumentException if the target is not below 100%, where the arithmetic
     *     has no solution
     */
    public static Money priceForTargetMargin(Money unitCost, int targetMarginPercent) {
        if (targetMarginPercent >= 100) {
            throw new IllegalArgumentException(
                    "a target margin of " + targetMarginPercent + "% has no finite price");
        }
        if (targetMarginPercent < 0) {
            throw new IllegalArgumentException("a target margin cannot be negative");
        }

        BigDecimal cost = BigDecimal.valueOf(unitCost.paise());
        BigDecimal remaining =
                BigDecimal.valueOf(100 - targetMarginPercent).divide(BigDecimal.valueOf(100));

        return Money.ofPaise(cost.divide(remaining, 0, RoundingMode.CEILING).longValueExact());
    }
}
