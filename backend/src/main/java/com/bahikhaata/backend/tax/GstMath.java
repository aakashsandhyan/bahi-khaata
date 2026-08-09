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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Extracting GST from a tax-inclusive price.
 *
 * <p>An Indian MRP is tax-inclusive by law, so a regular dealer never adds GST on top — it is
 * carved out of the price: for a line at rate {@code r}, tax = {@code price × r / (100 + r)}, and
 * the taxable value is the remainder. The invoice total therefore equals the sum of the selling
 * prices; nothing is inflated.
 *
 * <p>Rates are held in <strong>basis points</strong> (18% = 1800), an integer so odd slabs like
 * 0.25% (25) or 3% (300) are exact — the SQLite-friendly form of a decimal rate. Per-line tax is
 * computed exactly (fractional paise) and only the <em>invoice</em> total is rounded, to the nearest
 * rupee (CGST Act §170); rounding per line would accumulate paise drift and disagree with a
 * customer's hand check of the printed total. CGST and SGST each take half — this is a same-state
 * B2C counter, so never IGST.
 */
public final class GstMath {

    private GstMath() {}

    /** One priced line and the rate that applies to it, in basis points (18% = 1800). */
    public record Line(long pricePaise, int rateBasisPoints) {}

    /** The invoice-level GST breakdown, all in paise. */
    public record Breakdown(
            long subtotalPaise, long taxPaise, long cgstPaise, long sgstPaise, long taxablePaise) {}

    private static BigDecimal lineTaxExact(long pricePaise, int bp) {
        // price × bp / (10000 + bp), keeping fractional paise; bp is per-hundredth of a percent.
        return BigDecimal.valueOf(pricePaise)
                .multiply(BigDecimal.valueOf(bp))
                .divide(BigDecimal.valueOf(10_000L + bp), 6, RoundingMode.HALF_UP);
    }

    /** The per-line tax rounded to whole paise — for snapshotting on a sale line. */
    public static long lineTaxPaise(long pricePaise, int rateBasisPoints) {
        return lineTaxExact(pricePaise, rateBasisPoints)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * The invoice breakdown for a set of tax-inclusive lines: tax extracted per line at exact
     * precision, summed, then the invoice total rounded to the nearest rupee and split CGST/SGST.
     */
    public static Breakdown invoice(List<Line> lines) {
        long subtotal = lines.stream().mapToLong(Line::pricePaise).sum();
        BigDecimal taxExact = BigDecimal.ZERO;
        for (Line line : lines) {
            taxExact = taxExact.add(lineTaxExact(line.pricePaise(), line.rateBasisPoints()));
        }
        // Round the invoice tax to the nearest rupee (§170): paise → rupees, round, back to paise.
        long taxPaise =
                taxExact
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                        .longValueExact()
                        * 100;
        long cgst = taxPaise / 2;
        long sgst = taxPaise - cgst; // keeps CGST + SGST == tax exactly, even for an odd rupee
        long taxable = subtotal - taxPaise;
        return new Breakdown(subtotal, taxPaise, cgst, sgst, taxable);
    }
}
