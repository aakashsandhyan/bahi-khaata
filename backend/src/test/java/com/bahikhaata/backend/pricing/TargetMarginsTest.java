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
package com.bahikhaata.backend.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.bahikhaata.contracts.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-target-margins.db")
@Transactional
class TargetMarginsTest {

    @Autowired
    private TargetMargins margins;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Each category is seeded with its own target margin")
    void categoriesAreSeeded() {
        assertThat(margins.forCategory(Category.of("ELECTRONICS"))).contains(20);
        assertThat(margins.forCategory(Category.of("GIFTING"))).contains(40);
    }

    @Test
    @DisplayName("A category's margin wins over the global default")
    void categoryBeatsGlobal() {
        // The global default is 30; electronics is configured thinner.
        assertThat(margins.globalDefault()).isEqualTo(30);
        assertThat(margins.resolve(Category.of("ELECTRONICS"), null)).isEqualTo(20);
    }

    @Test
    @DisplayName("A category with no row of its own falls through to the global default")
    void missingCategoryFallsThrough() {
        jdbc.update("DELETE FROM category_margin WHERE category = ?", Category.of("KITCHEN").code());

        assertThat(margins.forCategory(Category.of("KITCHEN"))).isEmpty();
        assertThat(margins.resolve(Category.of("KITCHEN"), null)).isEqualTo(margins.globalDefault());
    }

    @Test
    @DisplayName("A custom margin wins over both")
    void customBeatsEverything() {
        // Typed while pricing one product, for that suggestion only.
        assertThat(margins.resolve(Category.of("ELECTRONICS"), 55)).isEqualTo(55);
        assertThat(margins.resolve(Category.of("KITCHEN"), 55)).isEqualTo(55);
    }

    @Test
    @DisplayName("A custom margin is never stored")
    void customIsNotStored() {
        int before = margins.forCategory(Category.of("ELECTRONICS")).orElseThrow();

        margins.resolve(Category.of("ELECTRONICS"), 55);

        // The price it produces is what gets stored; keeping the margin as well would be a
        // second source of truth for the same decision.
        assertThat(margins.forCategory(Category.of("ELECTRONICS"))).contains(before);
    }

    @Test
    @DisplayName("A changed category margin takes effect without a restart")
    void changesTakeEffectImmediately() {
        jdbc.update(
                "UPDATE category_margin SET target_margin_percent = ? WHERE category = ?",
                12,
                Category.of("ELECTRONICS").code());

        // The whole reason these live in the database rather than a properties file.
        assertThat(margins.resolve(Category.of("ELECTRONICS"), null)).isEqualTo(12);
    }
}
