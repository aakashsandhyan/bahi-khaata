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
package com.bahikhaata.contracts;

import java.util.regex.Pattern;

/**
 * The Indian GSTIN format: 15 characters — a two-digit state code, the ten-character PAN, a
 * registration digit, a fixed {@code Z}, and a checksum character.
 *
 * <p>Format only. A vendor may have no GSTIN at all, so this is applied solely when one is
 * present; a blank value passes as "none given".
 */
public final class Gstin {

    /** {@code 22AAAAA0000A1Z5} — state(2) PAN(10) entity(1) Z checksum(1). */
    public static final Pattern PATTERN =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private Gstin() {}

    /** True when {@code value} is null/blank (none given) or matches the GSTIN format. */
    public static boolean isValidOrAbsent(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return PATTERN.matcher(value.trim()).matches();
    }

    /**
     * Rejects a present-but-malformed GSTIN; accepts a blank one.
     *
     * @throws IllegalArgumentException if a non-blank value does not match the format
     */
    public static void requireValidOrAbsent(String value) {
        if (!isValidOrAbsent(value)) {
            throw new IllegalArgumentException("gstin is not a valid 15-character GSTIN: " + value);
        }
    }
}
