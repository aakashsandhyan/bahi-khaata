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

import static org.assertj.core.api.Assertions.assertThat;

import com.bahikhaata.contracts.Origin;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The {@link Origin} enum and the {@code CHECK} constraint on {@code barcode.origin} must name
 * the same set, for the same reason as the category constraint: a value in one but not the
 * other is an enum the database rejects or a database value the application cannot represent.
 */
class OriginCheckDriftTest {

    private static final String MIGRATION = "/db/migration/V3__product_and_barcode.sql";

    private static final Pattern CHECK_LIST =
            Pattern.compile(
                    "origin\\s+IN\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    @Test
    @DisplayName("The origin CHECK constraint lists exactly the Origin enum values")
    void checkConstraintMatchesEnum() throws Exception {
        Set<String> enumValues =
                Arrays.stream(Origin.values())
                        .map(Enum::name)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(originsInCheckConstraint()).isEqualTo(enumValues);
    }

    private Set<String> originsInCheckConstraint() throws Exception {
        String sql;
        try (InputStream in = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(in).as("migration %s on the classpath", MIGRATION).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        Matcher list = CHECK_LIST.matcher(sql);
        assertThat(list.find()).as("an `origin IN (...)` CHECK clause in the migration").isTrue();

        Set<String> values = new LinkedHashSet<>();
        Matcher quoted = QUOTED.matcher(list.group(1));
        while (quoted.find()) {
            values.add(quoted.group(1));
        }
        return values;
    }
}
