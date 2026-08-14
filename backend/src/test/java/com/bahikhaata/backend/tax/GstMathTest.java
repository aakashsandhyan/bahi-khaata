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
package com.bahikhaata.backend.tax;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GstMathTest {

    @Test
    @DisplayName("GST is extracted from the price, never added — the total stays the MRP-inclusive price")
    void extractsInclusively() {
        // ₹510 at 18% (1800 bp). Tax = 510 × 18 / 118 = ₹77.80, rounded to ₹78 at the invoice.
        GstMath.Breakdown b = GstMath.invoice(List.of(new GstMath.Line(51_000, 1800)));

        assertThat(b.subtotalPaise()).isEqualTo(51_000);
        assertThat(b.taxPaise()).isEqualTo(7_800); // ₹78, rounded to the rupee (§170)
        assertThat(b.taxablePaise()).isEqualTo(43_200); // ₹432 = ₹510 − ₹78
        assertThat(b.cgstPaise()).isEqualTo(3_900); // ₹39
        assertThat(b.sgstPaise()).isEqualTo(3_900); // ₹39
        // The point: the customer still pays ₹510, not ₹510 + tax.
        assertThat(b.taxablePaise() + b.taxPaise()).isEqualTo(b.subtotalPaise());
        assertThat(b.cgstPaise() + b.sgstPaise()).isEqualTo(b.taxPaise());
    }

    @Test
    @DisplayName("Mixed rates: tax is summed per line then rounded once at the invoice")
    void mixedRatesRoundedAtInvoice() {
        // ₹510 @18% + ₹200 @5% (500 bp): 5% tax = 200×5/105 = ₹9.52.
        GstMath.Breakdown b =
                GstMath.invoice(
                        List.of(new GstMath.Line(51_000, 1800), new GstMath.Line(20_000, 500)));

        assertThat(b.subtotalPaise()).isEqualTo(71_000); // total unchanged = sum of prices
        // 7779.66 + 952.38 = 8732.04 paise → ₹87.32 → ₹87 at the rupee.
        assertThat(b.taxPaise()).isEqualTo(8_700);
        assertThat(b.taxablePaise()).isEqualTo(62_300);
        assertThat(b.taxablePaise() + b.taxPaise()).isEqualTo(b.subtotalPaise());
    }

    @Test
    @DisplayName("A five-percent line extracts a fifth-of-118... its own rate, not 18%")
    void fivePercentLine() {
        GstMath.Breakdown b = GstMath.invoice(List.of(new GstMath.Line(21_000, 500)));
        // 210 × 5 / 105 = ₹10.00 exactly.
        assertThat(b.taxPaise()).isEqualTo(1_000);
        assertThat(b.taxablePaise()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("Decimal slabs are exact via basis points (0.25% = 25 bp)")
    void decimalSlab() {
        long tax = GstMath.lineTaxPaise(1_000_00, 25); // ₹1000 at 0.25%
        // 100000 × 25 / 10025 = 249.37 paise → 249.
        assertThat(tax).isEqualTo(249);
    }
}
