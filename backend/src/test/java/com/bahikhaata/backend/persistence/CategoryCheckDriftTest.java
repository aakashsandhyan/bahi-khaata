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

import com.bahikhaata.contracts.Category;
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
 * The {@link Category} enum and the {@code CHECK} constraint on {@code product.category} must
 * name the same set. They live apart — one in Java, one in a migration — and adding a
 * category to only one is a silent bug: an enum value the database rejects, or a database
 * value the application cannot represent. This fails the build when they diverge.
 */
class CategoryCheckDriftTest {

    private static final String MIGRATION = "/db/migration/V3__product_and_barcode.sql";

    // Captures the value list inside: CHECK (category IN ('A', 'B', ...))
    private static final Pattern CHECK_LIST =
            Pattern.compile(
                    "category\\s+IN\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    @Test
    @DisplayName("The category CHECK constraint lists exactly the Category enum values")
    void checkConstraintMatchesEnum() throws Exception {
        Set<String> enumValues =
                Arrays.stream(Category.values()).map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> checkValues = categoriesInCheckConstraint();

        assertThat(checkValues)
                .as("the CHECK constraint in %s must name exactly the Category enum values; "
                        + "a value in one set but not the other means an enum the database "
                        + "rejects or a database value the application cannot represent",
                        MIGRATION)
                .isEqualTo(enumValues);
    }

    private Set<String> categoriesInCheckConstraint() throws Exception {
        String sql;
        try (InputStream in = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(in).as("migration %s on the classpath", MIGRATION).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        Matcher list = CHECK_LIST.matcher(sql);
        assertThat(list.find()).as("a `category IN (...)` CHECK clause in the migration").isTrue();

        Set<String> values = new LinkedHashSet<>();
        Matcher quoted = QUOTED.matcher(list.group(1));
        while (quoted.find()) {
            values.add(quoted.group(1));
        }
        return values;
    }
}
