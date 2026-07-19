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
package com.bahikhaata.backend.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Executable form of the pattern in {@code docs/immutability-triggers.md}.
 *
 * <p>Deliberately raw JDBC with no Spring and no Hibernate. The specs require the
 * <em>database</em> to reject the write, so a test running through the ORM would prove the
 * wrong thing — it could pass on nothing more than {@code @Immutable} suppressing the
 * statement.
 *
 * <p>The tables here are throwaway stand-ins. Real tables get their triggers in the same
 * migration that creates them, in sections 4 and 6.
 */
class ImmutabilityTriggerPatternTest {

    @TempDir
    Path tempDir;

    private String url;

    @BeforeEach
    void createDatabase() throws Exception {
        url = "jdbc:sqlite:" + tempDir.resolve("triggers.db");
    }

    @AfterEach
    void removeDatabase() throws Exception {
        Files.deleteIfExists(tempDir.resolve("triggers.db"));
    }

    private void execute(String... statements) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.executeUpdate(sql);
            }
        }
    }

    private void executeExpectingAbort(String sql, String expectedFragment) {
        assertThatThrownBy(() -> execute(sql))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(expectedFragment);
    }

    private long rowCount(String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement();
                var rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String quantityOf(String id) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement();
                var rs =
                        statement.executeQuery(
                                "SELECT quantity FROM append_only_probe WHERE id = '" + id + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Nested
    @DisplayName("Immutable from creation")
    class ImmutableFromCreation {

        @BeforeEach
        void createTable() throws SQLException {
            execute(
                    """
                    CREATE TABLE append_only_probe (
                        id       CHAR(36) PRIMARY KEY,
                        quantity BIGINT NOT NULL
                    )
                    """,
                    """
                    CREATE TRIGGER append_only_probe_no_update
                    BEFORE UPDATE ON append_only_probe
                    BEGIN
                        SELECT RAISE(ABORT, 'append_only_probe is append-only: rows cannot be updated');
                    END
                    """,
                    """
                    CREATE TRIGGER append_only_probe_no_delete
                    BEFORE DELETE ON append_only_probe
                    BEGIN
                        SELECT RAISE(ABORT, 'append_only_probe is append-only: rows cannot be deleted');
                    END
                    """,
                    "INSERT INTO append_only_probe (id, quantity) VALUES ('row-1', 5)");
        }

        @Test
        void insertIsPermitted() throws SQLException {
            execute("INSERT INTO append_only_probe (id, quantity) VALUES ('row-2', 7)");

            assertThat(rowCount("append_only_probe")).isEqualTo(2);
        }

        @Test
        @DisplayName("UPDATE is refused and the row is untouched")
        void updateIsRefused() throws SQLException {
            executeExpectingAbort(
                    "UPDATE append_only_probe SET quantity = 999 WHERE id = 'row-1'",
                    "append-only: rows cannot be updated");

            assertThat(quantityOf("row-1")).isEqualTo("5");
        }

        @Test
        @DisplayName("DELETE is refused and the row remains")
        void deleteIsRefused() throws SQLException {
            executeExpectingAbort(
                    "DELETE FROM append_only_probe WHERE id = 'row-1'",
                    "append-only: rows cannot be deleted");

            assertThat(rowCount("append_only_probe")).isEqualTo(1);
        }

        @Test
        @DisplayName("A blanket UPDATE touching no matching row is still refused")
        void updateWithoutWhereIsRefused() throws SQLException {
            executeExpectingAbort(
                    "UPDATE append_only_probe SET quantity = 0", "cannot be updated");

            assertThat(quantityOf("row-1")).isEqualTo("5");
        }
    }

    @Nested
    @DisplayName("Immutable once issued")
    class ImmutableOnceIssued {

        @BeforeEach
        void createTable() throws SQLException {
            execute(
                    """
                    CREATE TABLE invoice_probe (
                        id        CHAR(36) PRIMARY KEY,
                        total     BIGINT NOT NULL,
                        issued_at TEXT
                    )
                    """,
                    """
                    CREATE TRIGGER invoice_probe_no_update_once_issued
                    BEFORE UPDATE ON invoice_probe
                    WHEN OLD.issued_at IS NOT NULL
                    BEGIN
                        SELECT RAISE(ABORT, 'invoice is immutable once issued: record a correction instead');
                    END
                    """,
                    """
                    CREATE TRIGGER invoice_probe_no_delete_once_issued
                    BEFORE DELETE ON invoice_probe
                    WHEN OLD.issued_at IS NOT NULL
                    BEGIN
                        SELECT RAISE(ABORT, 'invoice is immutable once issued: record a correction instead');
                    END
                    """,
                    "INSERT INTO invoice_probe (id, total, issued_at) VALUES ('draft', 1000, NULL)",
                    "INSERT INTO invoice_probe (id, total, issued_at) VALUES ('issued', 2000,"
                            + " '2026-07-19T10:00:00Z')");
        }

        @Test
        @DisplayName("An unissued invoice can still be changed")
        void draftIsEditable() throws SQLException {
            execute("UPDATE invoice_probe SET total = 1500 WHERE id = 'draft'");
            execute("DELETE FROM invoice_probe WHERE id = 'draft'");

            assertThat(rowCount("invoice_probe")).isEqualTo(1);
        }

        @Test
        void issuedInvoiceRefusesUpdate() {
            executeExpectingAbort(
                    "UPDATE invoice_probe SET total = 1 WHERE id = 'issued'",
                    "immutable once issued");
        }

        @Test
        void issuedInvoiceRefusesDelete() {
            executeExpectingAbort(
                    "DELETE FROM invoice_probe WHERE id = 'issued'", "immutable once issued");
        }

        @Test
        @DisplayName("Clearing issued_at cannot be used to unfreeze an invoice")
        void cannotUnfreezeByClearingIssuedAt() {
            // The trigger tests OLD.issued_at, the state before the statement. Testing
            // NEW would let this exact update through and reopen a finalised invoice.
            executeExpectingAbort(
                    "UPDATE invoice_probe SET issued_at = NULL WHERE id = 'issued'",
                    "immutable once issued");
        }

        @Test
        @DisplayName("Issuing a draft is permitted; changing it afterwards is not")
        void issuingThenEditing() throws SQLException {
            execute("UPDATE invoice_probe SET issued_at = '2026-07-19T11:00:00Z' WHERE id = 'draft'");

            executeExpectingAbort(
                    "UPDATE invoice_probe SET total = 9999 WHERE id = 'draft'",
                    "immutable once issued");
        }
    }
}
