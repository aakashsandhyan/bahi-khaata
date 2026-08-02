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
package com.bahikhaata.backend.health;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The health endpoint reports 503 DOWN, not a healthy 200, when the database cannot be read.
 *
 * <p>A controller slice with a failing {@link JdbcTemplate} rather than a full context: once
 * real entities exist, an unmigrated database cannot start the application at all — {@code
 * ddl-auto=validate} aborts on the missing schema — so the only reachable DOWN path is a
 * database that becomes unreadable after a healthy start. That is what this simulates, and it
 * is the state the terminal must never mistake for ready.
 */
@WebMvcTest(HealthController.class)
class HealthControllerUnmigratedTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("An unreadable database reports 503, not a healthy 200")
    void reportsServiceUnavailable() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(String.class)))
                .thenThrow(new CannotGetJdbcConnectionException("database unreachable"));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.schemaVersion").doesNotExist());
    }
}
