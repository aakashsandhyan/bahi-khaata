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
package com.bahikhaata.backend.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstantIso8601ConverterTest {

    private final InstantIso8601Converter converter = new InstantIso8601Converter();

    @Test
    @DisplayName("An instant stores as readable ISO-8601 UTC text")
    void storesReadableText() {
        Instant instant = Instant.parse("2026-07-20T10:04:35.821Z");
        assertThat(converter.convertToDatabaseColumn(instant)).isEqualTo("2026-07-20T10:04:35.821Z");
    }

    @Test
    @DisplayName("Fractional seconds are always three digits, so text sort stays chronological")
    void fixedWidthKeepsSortChronological() {
        // The trap: a variable-width fraction breaks ORDER BY on the text column, because
        // '.' (0x2E) sorts before 'Z' (0x5A). An earlier instant with no fraction would
        // then sort AFTER a later one that has a fraction.
        String earlier = converter.convertToDatabaseColumn(Instant.parse("2026-07-20T10:04:35Z"));
        String later = converter.convertToDatabaseColumn(Instant.parse("2026-07-20T10:04:35.500Z"));

        assertThat(earlier).isEqualTo("2026-07-20T10:04:35.000Z");
        assertThat(later).isEqualTo("2026-07-20T10:04:35.500Z");
        // Plain string comparison must agree with time order.
        assertThat(earlier.compareTo(later)).isNegative();
    }

    @Test
    @DisplayName("Round-trips to the same millisecond")
    void roundTrips() {
        Instant instant = Instant.parse("2026-07-20T10:04:35.821Z");
        String stored = converter.convertToDatabaseColumn(instant);
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(instant);
    }

    @Test
    @DisplayName("Null passes through both ways")
    void nullPassesThrough() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
