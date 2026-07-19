/*
 * bahi-khaata — point of sale for Bachat Bazar
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
package com.bahikhaata.contracts;

import java.util.Objects;

/**
 * An exact rupee amount, held as a whole number of paise.
 *
 * <p>Money never touches {@code double} or {@code float}. SQLite has no true decimal
 * type and applies type affinity, so a value stored as floating point can come back
 * inexact — unacceptable when the result is printed on a tax invoice. Every amount in
 * this system is therefore an integer count of the smallest unit, and all arithmetic
 * here is integer arithmetic.
 *
 * <p>Arithmetic throws on overflow rather than wrapping. A silently negative total is
 * far worse than a failed request.
 *
 * <p>Lives in {@code contracts} because backend, terminal and dashboard all need the
 * same notion of an amount, and duplicating rounding rules across three modules is how
 * they drift apart.
 */
public record Money(long paise) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    private static final long PAISE_PER_RUPEE = 100;

    public static Money ofPaise(long paise) {
        return new Money(paise);
    }

    public static Money ofRupees(long rupees) {
        return new Money(Math.multiplyExact(rupees, PAISE_PER_RUPEE));
    }

    /**
     * Parses a rupee amount such as {@code 1234.56}, {@code ₹1,234.56} or {@code -5}.
     *
     * <p>At most two decimal places are accepted. More would mean a fraction of a paise,
     * which does not exist — rejecting it is better than quietly discarding it.
     */
    public static Money parse(String text) {
        Objects.requireNonNull(text, "amount text");

        String cleaned = text.trim().replace("₹", "").replace(",", "").replace(" ", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Not a rupee amount: \"" + text + "\"");
        }

        boolean negative = cleaned.startsWith("-");
        if (negative || cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
        }

        String rupeePart = cleaned;
        String paisePart = "00";

        int dot = cleaned.indexOf('.');
        if (dot >= 0) {
            rupeePart = cleaned.substring(0, dot);
            paisePart = cleaned.substring(dot + 1);
            if (paisePart.isEmpty() || paisePart.length() > 2) {
                throw new IllegalArgumentException(
                        "Rupee amounts have at most two decimal places: \"" + text + "\"");
            }
            // "1.5" means fifty paise, not five.
            if (paisePart.length() == 1) {
                paisePart = paisePart + "0";
            }
        }

        if (rupeePart.isEmpty() || !isAllDigits(rupeePart) || !isAllDigits(paisePart)) {
            throw new IllegalArgumentException("Not a rupee amount: \"" + text + "\"");
        }

        long total = Math.addExact(
                Math.multiplyExact(Long.parseLong(rupeePart), PAISE_PER_RUPEE),
                Long.parseLong(paisePart));

        return new Money(negative ? Math.negateExact(total) : total);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(this.paise, other.paise));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(this.paise, other.paise));
    }

    public Money times(long factor) {
        return new Money(Math.multiplyExact(this.paise, factor));
    }

    public Money negated() {
        return new Money(Math.negateExact(paise));
    }

    public Money abs() {
        return paise < 0 ? negated() : this;
    }

    public boolean isZero() {
        return paise == 0;
    }

    public boolean isNegative() {
        return paise < 0;
    }

    public boolean isPositive() {
        return paise > 0;
    }

    /**
     * Rounds to the nearest whole rupee under section 170 of the CGST Act: fifty paise
     * and above rounds up, below fifty rounds down.
     *
     * <p>Applied once when an invoice is issued, never incrementally as lines accumulate.
     * Rounding each line and then summing drifts from the true total, and that difference
     * is what a customer notices at the counter.
     *
     * <p>Rounding is away from zero, so a negative amount of exactly fifty paise rounds to
     * the larger magnitude. Reversing entries then mirror the original rather than
     * differing by a rupee.
     */
    public Money roundToRupee() {
        long remainder = paise % PAISE_PER_RUPEE;
        if (remainder == 0) {
            return this;
        }

        long truncated = paise - remainder;
        if (Math.abs(remainder) >= 50) {
            truncated += paise < 0 ? -PAISE_PER_RUPEE : PAISE_PER_RUPEE;
        }
        return new Money(truncated);
    }

    /** Machine-readable, ungrouped, always two decimals — {@code -1234.56}. */
    public String toPlainString() {
        long magnitude = Math.abs(paise);
        return (paise < 0 ? "-" : "")
                + (magnitude / PAISE_PER_RUPEE)
                + "."
                + String.format("%02d", magnitude % PAISE_PER_RUPEE);
    }

    /**
     * Human-readable, with the rupee sign and Indian digit grouping — {@code ₹12,34,567.89}.
     *
     * <p>Indian grouping is three digits then pairs, not groups of three. A customer
     * reading a total in the wrong grouping has to stop and count zeros.
     */
    public String format() {
        long magnitude = Math.abs(paise);
        String grouped = groupIndian(Long.toString(magnitude / PAISE_PER_RUPEE));
        String fraction = String.format("%02d", magnitude % PAISE_PER_RUPEE);
        return (paise < 0 ? "-" : "") + "₹" + grouped + "." + fraction;
    }

    private static String groupIndian(String digits) {
        if (digits.length() <= 3) {
            return digits;
        }
        String lastThree = digits.substring(digits.length() - 3);
        String rest = digits.substring(0, digits.length() - 3);

        StringBuilder out = new StringBuilder();
        int index = rest.length();
        while (index > 2) {
            out.insert(0, "," + rest.substring(index - 2, index));
            index -= 2;
        }
        out.insert(0, rest.substring(0, index));

        return out + "," + lastThree;
    }

    private static boolean isAllDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(this.paise, other.paise);
    }

    @Override
    public String toString() {
        return format();
    }
}
