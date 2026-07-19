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
package com.bahikhaata.backend.health;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-health-up.db")
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("A migrated database reports UP with the latest applied schema version")
    void reportsUp() throws Exception {
        // Assert against the actual latest migration rather than a literal, so adding a
        // migration does not break this test — it is the endpoint reporting the current
        // version that matters, not a specific number.
        String latest =
                jdbc.queryForObject(
                        "SELECT version FROM flyway_schema_history WHERE success = 1 "
                                + "ORDER BY installed_rank DESC LIMIT 1",
                        String.class);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.schemaVersion").value(latest));
    }

    @Test
    @DisplayName("The wire shape is exactly the record's components")
    void carriesNoUnintendedFields() throws Exception {
        // A helper method on a record becomes a JSON property. This caught a derived
        // "up" field that duplicated — and could contradict — "status".
        mockMvc.perform(get("/api/health"))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.up").doesNotExist());
    }
}
