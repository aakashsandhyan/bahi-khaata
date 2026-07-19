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
