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
package com.bahikhaata.backend.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * Stores an {@link Instant} as ISO-8601 UTC text — {@code 2026-07-20T10:04:35.821Z}.
 *
 * <p>Readable in a raw {@code sqlite3} session, for the same reason ids are CHAR(36) and
 * categories are text (design decision 7): the columns most often eyeballed — when was this
 * created, when last changed — should be legible, not an opaque epoch integer.
 *
 * <p>The format uses exactly three fractional digits, always. Variable-width fractions break
 * lexical ordering — {@code ...35Z} would sort after {@code ...35.500Z} because 'Z' &gt; '.'
 * — so a fixed width keeps text sort chronological, which is what makes a TEXT timestamp
 * safe to ORDER BY. Precision is milliseconds; sub-millisecond detail is not meaningful for
 * created/updated stamps and is deliberately dropped.
 */
@Converter
public class InstantIso8601Converter implements AttributeConverter<Instant, String> {

    private static final DateTimeFormatter ISO_MILLIS =
            new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    @Override
    public String convertToDatabaseColumn(Instant instant) {
        return instant == null ? null : ISO_MILLIS.format(instant);
    }

    @Override
    public Instant convertToEntityAttribute(String text) {
        return text == null ? null : Instant.parse(text);
    }
}
