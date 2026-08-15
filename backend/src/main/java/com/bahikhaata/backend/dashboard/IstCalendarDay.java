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
package com.bahikhaata.backend.dashboard;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * "Today" for the revenue-today tile: an IST calendar day, converted to the UTC ISO-8601 text
 * range that {@code sale.created_at} can be compared against directly.
 *
 * <p>The shop runs in IST ({@code Asia/Kolkata}, UTC+5:30), but every timestamp in the database is
 * stored as UTC text. A sale rung up at 20:00 UTC is already 01:30 IST the next day — SQLite's
 * {@code date(created_at)} on the raw UTC text would file it under the wrong day, dropping it from
 * (or misattributing it into) "today". Computing the IST day boundary in Java first and converting
 * that to two UTC instants sidesteps the problem entirely: the comparison stays a plain half-open
 * text range, {@code created_at >= startUtc AND created_at < endUtc}, which is exact and can use
 * {@code idx_sale_created_at} because the stored column never needs a function applied to it.
 */
final class IstCalendarDay {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // The same fixed-three-fractional-digit format every other timestamp in this database is
    // stored in (see InstantIso8601Converter) — required so the string range compares correctly
    // against created_at, which is text, not a real datetime type.
    private static final DateTimeFormatter ISO_MILLIS =
            new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    private IstCalendarDay() {}

    /** The half-open UTC instant range {@code [startUtc, endUtc)} of the IST day containing {@code instant}. */
    static Window windowContaining(Instant instant) {
        ZonedDateTime startOfIstDay = instant.atZone(IST).toLocalDate().atStartOfDay(IST);
        ZonedDateTime startOfNextIstDay = startOfIstDay.plusDays(1);
        return new Window(
                ISO_MILLIS.format(startOfIstDay.toInstant()),
                ISO_MILLIS.format(startOfNextIstDay.toInstant()));
    }

    /** {@code startUtc} and {@code endUtc} as ISO-8601-Z text, ready to bind into a SQL query. */
    record Window(String startUtc, String endUtc) {}
}
