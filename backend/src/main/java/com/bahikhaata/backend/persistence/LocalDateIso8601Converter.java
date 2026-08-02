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

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;

/**
 * Stores a {@link LocalDate} as ISO-8601 text — {@code 2026-07-20}.
 *
 * <p>Readable in a raw {@code sqlite3} session, matching how instants are stored. The ISO
 * form is fixed width, so text ordering is chronological — which matters here because FIFO
 * consumes batches by their lot's delivery date, and that ordering is done in SQL.
 */
@Converter
public class LocalDateIso8601Converter implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate date) {
        // LocalDate.toString() is always yyyy-MM-dd — no variable-width fraction to trip
        // lexical ordering, unlike an instant.
        return date == null ? null : date.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String text) {
        return text == null ? null : LocalDate.parse(text);
    }
}
