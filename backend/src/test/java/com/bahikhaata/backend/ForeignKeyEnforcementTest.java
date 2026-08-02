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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * SQLite disables foreign-key enforcement on every new connection, so a {@code REFERENCES}
 * clause is inert unless {@code PRAGMA foreign_keys=ON} is set per connection. That pragma
 * is configured as Hikari's connection-init SQL; this confirms it actually took effect on
 * the application's datasource — which a separate {@code sqlite3} session cannot show,
 * because the state is per-connection.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-fk.db")
class ForeignKeyEnforcementTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Foreign keys are ON for the application's connection")
    void foreignKeysAreOn() {
        assertThat(jdbc.queryForObject("PRAGMA foreign_keys", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("An orphan barcode is rejected, not silently stored")
    void orphanBarcodeIsRejected() {
        // The bug this guards: a barcode pointing at a product that does not exist. With
        // enforcement off it would insert cleanly and corrupt the catalogue.
        //
        // Spring maps the failure to an UncategorizedSQLException rather than
        // DataIntegrityViolationException — SQLite ships no error-code translation table,
        // so the constraint code is not classified. What matters is that the write is
        // refused; we assert on the DataAccessException hierarchy and the SQLite message.
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO barcode (id, product_id, code, origin, created_at) "
                                                + "VALUES (?, ?, ?, ?, ?)",
                                        "bc-orphan",
                                        "no-such-product",
                                        "TEST-CODE-1",
                                        "INTERNAL",
                                        "2026-07-20T10:00:00Z"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("FOREIGN KEY constraint failed");
    }
}
