/*
 * bahi-khaata — point of sale for Bachat Bazar
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
package com.bahikhaata.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Task 1.5 — a clean database reaches a known version unattended.
 *
 * <p>The database file is deleted before the context starts, so this exercises the
 * genuine first-run path on a fresh machine rather than a database some earlier
 * test left behind.
 */
@SpringBootTest
class FlywayMigrationTest {

    private static final Path DB = Paths.get("build/test-flyway.db");

    @DynamicPropertySource
    static void freshDatabase(DynamicPropertyRegistry registry) throws IOException {
        // Runs before the context is created, so the application really does start
        // against nothing. WAL leaves sidecar files that must go with it.
        Files.deleteIfExists(DB);
        Files.deleteIfExists(Paths.get(DB + "-wal"));
        Files.deleteIfExists(Paths.get(DB + "-shm"));
        registry.add("bahikhaata.db.path", DB::toString);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("A clean database is migrated to a known version at startup")
    void cleanDatabaseReachesKnownVersion() {
        List<Map<String, Object>> history =
                jdbc.queryForList(
                        "SELECT version, description, success FROM flyway_schema_history "
                                + "ORDER BY installed_rank");

        assertThat(history).hasSize(2);
        assertThat(history.get(0)).containsEntry("version", "1");
        assertThat(history.get(0)).containsEntry("description", "baseline");
        assertThat(history.get(1)).containsEntry("version", "2");
        assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(1));
    }

    @Test
    @DisplayName("Migration is idempotent — a second run applies nothing new")
    void secondRunAppliesNothing() {
        Integer applied =
                jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);

        // The context has already started once. Flyway records each application
        // exactly once, so a restart against this database must not add rows.
        assertThat(applied).isEqualTo(2);
    }
}
