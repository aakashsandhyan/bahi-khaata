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
package com.bahikhaata.backend.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.contracts.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarginsTest {

    @Test
    @DisplayName("Gross margin is over price, and is not markup")
    void marginIsNotMarkup() {
        // Bought at ₹60, sold at ₹100. Margin is 40 ÷ 100 = 40%; markup is 40 ÷ 60 = 67%.
        // Getting these the wrong way round is a standard retail arithmetic error, and here
        // it would make the review threshold fire at roughly the wrong sensitivity.
        BigDecimal margin = Margins.grossMarginPercent(Money.ofRupees(100), Money.ofRupees(60));

        assertThat(margin).isEqualByComparingTo("40.00");
        assertThat(margin).isNotEqualByComparingTo("66.67");
    }

    @Test
    @DisplayName("A cost equal to the price is a zero margin")
    void breakEvenIsZero() {
        assertThat(Margins.grossMarginPercent(Money.ofRupees(100), Money.ofRupees(100)))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Selling below cost is a negative margin, not an error")
    void belowCostIsNegative() {
        // A real and important state: it is what the review threshold exists to surface.
        assertThat(Margins.grossMarginPercent(Money.ofRupees(100), Money.ofRupees(130)))
                .isEqualByComparingTo("-30.00");
    }

    @Test
    @DisplayName("Margin without a price is undefined, not zero")
    void undefinedWithoutAPrice() {
        // Returning zero would read as "breaking even" for a product nobody has priced.
        assertThatThrownBy(() -> Margins.grossMarginPercent(null, Money.ofRupees(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Margins.grossMarginPercent(Money.ZERO, Money.ofRupees(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A price for a target margin achieves that margin")
    void priceAchievesTheTargetMargin() {
        // ₹70 at a 30% target: 70 ÷ 0.7 = ₹100.
        Money price = Margins.priceForTargetMargin(Money.ofRupees(70), 30);

        assertThat(price).isEqualTo(Money.ofRupees(100));
        assertThat(Margins.grossMarginPercent(price, Money.ofRupees(70)))
                .isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("A suggested price rounds up, so the target is met rather than missed")
    void suggestedPriceRoundsUp() {
        // ₹18.75 at 30%: 18.75 ÷ 0.7 = ₹26.7857…, so ₹26.79 rather than ₹26.78.
        Money price = Margins.priceForTargetMargin(Money.ofPaise(1_875), 30);

        assertThat(price).isEqualTo(Money.ofPaise(2_679));
        // Rounding down would land just under the target; rounding up clears it.
        assertThat(Margins.grossMarginPercent(price, Money.ofPaise(1_875)))
                .isGreaterThanOrEqualTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("A zero target margin prices at cost")
    void zeroTargetPricesAtCost() {
        assertThat(Margins.priceForTargetMargin(Money.ofRupees(70), 0)).isEqualTo(Money.ofRupees(70));
    }

    @Test
    @DisplayName("A target margin of 100% or more has no finite price")
    void impossibleTargetIsRefused() {
        // price = cost ÷ (1 − margin), which divides by zero at 100%.
        assertThatThrownBy(() -> Margins.priceForTargetMargin(Money.ofRupees(70), 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Margins.priceForTargetMargin(Money.ofRupees(70), -5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
