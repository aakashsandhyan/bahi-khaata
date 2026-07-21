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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.LotState;
import com.bahikhaata.contracts.Marketplace;
import com.bahikhaata.contracts.MovementType;
import com.bahikhaata.contracts.Origin;
import com.bahikhaata.contracts.StockCondition;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every governed enum and the {@code CHECK} constraint on its column must name the same set.
 *
 * <p>They live apart — one in Java, one in a migration — and adding a value to only one is a
 * silent bug: an enum value the database rejects, or a database value the application cannot
 * represent. This fails the build when they diverge.
 *
 * <p>One table for all of them rather than a class per enum: the check is identical in every
 * case, and a new governed enum should cost one row here, not another near-copy of the same
 * parsing.
 *
 * <p>Category is deliberately absent. It was an enum until a real consignment showed the set
 * was not fixed; it is now a lookup table, governed by a foreign key rather than a CHECK, so
 * there is no list in a migration for it to drift from.
 */
class EnumCheckConstraintDriftTest {

    private static final String PRODUCTS = "/db/migration/V3__product_and_barcode.sql";
    private static final String LOTS = "/db/migration/V5__lot_and_batch.sql";
    private static final String LEDGER = "/db/migration/V6__stock_ledger.sql";
    private static final String ONLINE_PRICE = "/db/migration/V11__product_online_price.sql";
    private static final String LOT_STATE =
            "/db/migration/V12__expectation_boxes_and_lot_state.sql";
    private static final String BATCH_COST =
            "/db/migration/V13__batch_cost_becomes_nullable.sql";

    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    static Stream<Arguments> governedEnums() {
        return Stream.of(
                arguments(Origin.class, "origin", "/db/migration/V19__unit_label_origin.sql"),
                arguments(AllocationMethod.class, "allocation_method", LOTS),
                arguments(MovementType.class, "movement_type", LEDGER),
                arguments(Marketplace.class, "online_price_source", ONLINE_PRICE),
                arguments(LotState.class, "state", LOT_STATE),
                // CostBasis moved to V13 when the batch table was rebuilt, and gained
                // ESTIMATED there. V5's list is dead text now; this must read the live one.
                arguments(CostBasis.class, "cost_basis", BATCH_COST),
                arguments(
                        StockCondition.class,
                        "condition",
                        "/db/migration/V17__stock_condition.sql"));
    }

    @ParameterizedTest(name = "{0} matches the {1} CHECK constraint")
    @MethodSource("governedEnums")
    void checkConstraintMatchesEnum(Class<? extends Enum<?>> enumType, String column, String migration)
            throws Exception {
        Set<String> enumValues =
                Arrays.stream(enumType.getEnumConstants())
                        .map(Enum::name)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(valuesInCheckConstraint(column, migration))
                .as(
                        "the %s CHECK constraint in %s must name exactly the %s values; a value "
                                + "in one set but not the other means an enum the database rejects "
                                + "or a database value the application cannot represent",
                        column, migration, enumType.getSimpleName())
                .isEqualTo(enumValues);
    }

    private Set<String> valuesInCheckConstraint(String column, String migration) throws Exception {
        String sql;
        try (InputStream in = getClass().getResourceAsStream(migration)) {
            assertThat(in).as("migration %s on the classpath", migration).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        Matcher list =
                Pattern.compile(
                                column + "\\s+IN\\s*\\(([^)]*)\\)",
                                Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                        .matcher(sql);
        assertThat(list.find())
                .as("a `%s IN (...)` CHECK clause in %s", column, migration)
                .isTrue();

        Set<String> values = new LinkedHashSet<>();
        Matcher quoted = QUOTED.matcher(list.group(1));
        while (quoted.find()) {
            values.add(quoted.group(1));
        }
        return values;
    }
}
