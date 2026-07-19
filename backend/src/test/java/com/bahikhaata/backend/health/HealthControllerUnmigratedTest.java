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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The endpoint must not claim readiness it does not have. Flyway is disabled here, so
 * {@code flyway_schema_history} is absent and the check fails the way it would against a
 * database that is present but unusable.
 */
@SpringBootTest(
        properties = {
            "bahikhaata.db.path=build/test-health-down.db",
            "spring.flyway.enabled=false"
        })
@AutoConfigureMockMvc
class HealthControllerUnmigratedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("An unmigrated database reports 503, not a healthy 200")
    void reportsServiceUnavailable() throws Exception {
        // Guards the failure that hurts: a running process reporting healthy while it
        // cannot serve a sale, so the cashier finds out on the first scan.
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.schemaVersion").doesNotExist());
    }
}
