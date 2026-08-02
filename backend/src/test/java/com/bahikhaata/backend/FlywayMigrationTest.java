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
    @DisplayName("A clean database migrates cleanly to a gapless, all-successful history")
    void cleanDatabaseReachesKnownVersion() {
        List<Map<String, Object>> history =
                jdbc.queryForList(
                        "SELECT version, description, success FROM flyway_schema_history "
                                + "ORDER BY installed_rank");

        // Deliberately count-agnostic: adding a migration must not break this test.
        // What matters is that a fresh database reaches a coherent state — V1 first,
        // every migration successful, and versions consecutive with no gaps.
        assertThat(history).isNotEmpty();
        assertThat(history.get(0)).containsEntry("version", "1");
        assertThat(history.get(0)).containsEntry("description", "baseline");
        assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(1));

        for (int i = 0; i < history.size(); i++) {
            assertThat(history.get(i))
                    .as("migration at position %d", i)
                    .containsEntry("version", String.valueOf(i + 1));
        }
    }

    @Test
    @DisplayName("Every applied migration is recorded exactly once")
    void eachMigrationRecordedOnce() {
        Integer rows =
                jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        Integer distinctVersions =
                jdbc.queryForObject(
                        "SELECT COUNT(DISTINCT version) FROM flyway_schema_history", Integer.class);

        // Flyway records each application once; a duplicated version would mean a
        // migration ran twice, which is drift.
        assertThat(rows).isEqualTo(distinctVersions);
    }
}
