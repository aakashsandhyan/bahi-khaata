package com.bahikhaata.backend.health;

import com.bahikhaata.contracts.HealthResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Readiness for the terminal to poll on startup.
 *
 * <p>Hand-written rather than Spring Boot Actuator. Actuator would give this for free, but
 * it also brings a surface area of endpoints this application does not want, and the
 * terminal needs a response shape that {@code contracts} can define. One controller is
 * cheaper to read than an actuator configuration that says which of its endpoints are off.
 *
 * <p>The check queries the schema history rather than pinging the connection, because
 * reachable and migrated are different things. A database that answers but has no schema
 * cannot serve a sale.
 */
@RestController
@RequestMapping("/api/health")
class HealthController {

    private static final String LATEST_APPLIED_VERSION =
            "SELECT version FROM flyway_schema_history "
                    + "WHERE success = 1 "
                    // Ordered by application order, not by version: version is text, so
                    // MAX() would rank "9" above "10".
                    + "ORDER BY installed_rank DESC LIMIT 1";

    private final JdbcTemplate jdbc;

    HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    ResponseEntity<HealthResponse> health() {
        try {
            String version = jdbc.queryForObject(LATEST_APPLIED_VERSION, String.class);
            return ResponseEntity.ok(HealthResponse.up(version));
        } catch (DataAccessException notUsable) {
            // Deliberately 503 rather than 200-with-DOWN. The terminal treats any
            // non-success as "not ready yet", so an unreachable backend and a broken
            // one take the same path and neither can be mistaken for ready.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(HealthResponse.down());
        }
    }
}
