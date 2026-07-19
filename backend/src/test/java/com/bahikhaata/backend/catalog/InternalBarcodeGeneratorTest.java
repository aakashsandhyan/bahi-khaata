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
package com.bahikhaata.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.Origin;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional so each method rolls back to the seeded counter (last_seq = 99999) and shares
 * no state: the counter is a single mutable row, and without rollback one test's allocations
 * would advance it under the next, breaking the exact-value assertions and colliding on the
 * unique code.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-internal-barcode.db")
@Transactional
class InternalBarcodeGeneratorTest {

    @Autowired
    private ProductRepository products;

    @Autowired
    private InternalBarcodeGenerator generator;

    @Autowired
    private JdbcTemplate jdbc;

    private Product newProduct(String name) {
        return products.save(new Product(name, Category.HOME_ESSENTIALS, Map.of()));
    }

    @Test
    @DisplayName("The first generated code is BBZ-100000 with INTERNAL origin")
    void firstCodeIsExpected() {
        Barcode generated = generator.generateFor(newProduct("Loose item"));

        assertThat(generated.getCode()).isEqualTo("BBZ-100000");
        assertThat(generated.getOrigin()).isEqualTo(Origin.INTERNAL);
    }

    @Test
    @DisplayName("Successive codes are distinct and consecutive")
    void successiveCodesDiffer() {
        String first = generator.generateFor(newProduct("A")).getCode();
        String second = generator.generateFor(newProduct("B")).getCode();

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo("BBZ-100000");
        assertThat(second).isEqualTo("BBZ-100001");
    }

    @Test
    @DisplayName("A generated code contains letters, so it can never be an all-numeric EAN-13")
    void codeIsNeverAllDigits() {
        String code = generator.generateFor(newProduct("C")).getCode();

        // The structural collision guarantee: a manufacturer code is all digits; this never
        // is, so the two character sets cannot overlap.
        assertThat(code).matches("BBZ-\\d{6}");
        assertThat(code).containsPattern("[A-Za-z]");
    }

    @Test
    @DisplayName("The range is fixed six digits: exhaustion fails loudly, not with a 7th digit")
    void exhaustionFailsLoudly() {
        // Fast-forward the counter to its last valid value, so the next allocation is 1000000.
        jdbc.update("UPDATE internal_barcode_counter SET last_seq = 999999 WHERE id = 1");

        assertThatThrownBy(() -> generator.generateFor(newProduct("D")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exhausted");
    }
}
