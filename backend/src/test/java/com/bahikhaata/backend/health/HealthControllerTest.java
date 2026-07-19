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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-health-up.db")
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("A migrated database reports UP with its schema version")
    void reportsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.schemaVersion").value("1"));
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
