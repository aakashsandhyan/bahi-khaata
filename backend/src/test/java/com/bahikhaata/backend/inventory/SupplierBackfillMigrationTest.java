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
package com.bahikhaata.backend.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Task 7.3 — the V38 backfill collapses distinct normalised supplier strings into one supplier
 * each and links every lot.
 *
 * <p>V38 runs at startup against an empty lot table, so there is nothing to back-fill then. To
 * exercise the real backfill this test seeds legacy-style lot rows — a supplier string, a null
 * {@code supplier_id} — and re-runs V38's two backfill statements (reproduced verbatim below)
 * against real SQLite, then asserts the outcome.
 */
@SpringBootTest
class SupplierBackfillMigrationTest {

    private static final Path DB = Paths.get("build/test-supplier-backfill.db");

    @DynamicPropertySource
    static void freshDatabase(DynamicPropertyRegistry registry) throws IOException {
        Files.deleteIfExists(DB);
        Files.deleteIfExists(Paths.get(DB + "-wal"));
        Files.deleteIfExists(Paths.get(DB + "-shm"));
        registry.add("bahikhaata.db.path", DB::toString);
    }

    @Autowired private JdbcTemplate jdbc;

    // Reproduced verbatim from V38__supplier_and_lot_link.sql.
    private static final String BACKFILL_INSERT =
            "INSERT INTO supplier (id, name, name_normalized, gstin, phone, address, contact_person, notes, active, created_at, updated_at) "
                    + "SELECT lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || "
                    + "substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))), "
                    + "MIN(supplier), lower(trim(supplier)), NULL, NULL, NULL, NULL, NULL, true, "
                    + "strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now') "
                    + "FROM lot GROUP BY lower(trim(supplier))";

    private static final String BACKFILL_UPDATE =
            "UPDATE lot SET supplier_id = (SELECT s.id FROM supplier s WHERE s.name_normalized = lower(trim(lot.supplier)))";

    private void seedLot(String supplier) {
        String now = "2026-07-20T00:00:00.000Z";
        jdbc.update(
                "INSERT INTO lot (id, supplier, received_on, amount_paid_paise, freight_paise, allocation_method, state, created_at, updated_at, receiving_complete, is_manual) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), supplier, "2026-07-20", 100000, 0,
                "RELATIVE_MRP", "OPEN", now, now, false, false);
    }

    @Test
    @DisplayName("Distinct normalised strings collapse to one supplier each and every lot is linked")
    void backfillsAndLinks() {
        seedLot("ABC");
        seedLot("abc ");        // same as ABC once trimmed and lower-cased
        seedLot("XYZ Traders");

        jdbc.update(BACKFILL_INSERT);
        jdbc.update(BACKFILL_UPDATE);

        Integer supplierCount = jdbc.queryForObject("SELECT COUNT(*) FROM supplier", Integer.class);
        assertThat(supplierCount).isEqualTo(2);

        Integer unlinked =
                jdbc.queryForObject("SELECT COUNT(*) FROM lot WHERE supplier_id IS NULL", Integer.class);
        assertThat(unlinked).isEqualTo(0);

        // The two ABC lots resolve to the same supplier; the XYZ lot to a different one.
        Integer distinctSuppliersUsed =
                jdbc.queryForObject("SELECT COUNT(DISTINCT supplier_id) FROM lot", Integer.class);
        assertThat(distinctSuppliersUsed).isEqualTo(2);

        Integer abcLots =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM lot WHERE supplier_id = "
                                + "(SELECT id FROM supplier WHERE name_normalized = 'abc')",
                        Integer.class);
        assertThat(abcLots).isEqualTo(2);
    }
}
