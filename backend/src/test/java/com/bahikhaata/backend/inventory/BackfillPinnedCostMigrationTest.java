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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The V37 backfill: pinning cost onto stock counted before the model changed.
 *
 * <p>Those batches sit costed at nothing, so nothing in their lots can be priced on margin. The
 * migration settles them the same way the counting path now does — the product's stated value
 * scaled by the rate its lot was bought at. The booted application only proves the statement
 * parses; a fresh test database holds no pre-change stock for it to touch. So this reproduces
 * that state deliberately — a batch pinned at receipt, then stripped back to uncosted as the old
 * code left it — runs the very SQL the migration ships, and checks it arrives at exactly the cost
 * the live path did. The two must not drift.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-backfill.db")
@Transactional
class BackfillPinnedCostMigrationTest {

    private static final Instant AT = Instant.parse("2026-07-21T09:00:00Z");

    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BatchRepository batches;
    @Autowired private BarcodeRepository barcodes;
    @Autowired private LotRepository lots;
    @Autowired private SupplierRepository suppliers;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private static ImportLine line(String box, String code, long qty, long unitValuePaise) {
        return new ImportLine(code, code, qty, unitValuePaise, null, box, null, null);
    }

    private String supplierId(String name) {
        return suppliers.findByNameNormalized(Supplier.normalize(name))
                .map(Supplier::getId)
                .orElseGet(() -> suppliers.save(new Supplier(name, null, null, null, null, null)).getId())
                .toString();
    }

    private UUID importLot(String category, long paidPaise, List<ImportLine> lines) {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Sushil"), "2026-07-17",
                        List.of(new ImportLot(category, paidPaise, AllocationMethod.RELATIVE_MRP, lines))));
        return lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b).orElseThrow().getId();
    }

    private void count(UUID lotId, String code, long quantity) {
        UUID lineId = expectedLines.findByLotIdOrderByCode(lotId).stream()
                .filter(l -> l.getCode().equals(code)).findFirst().orElseThrow().getId();
        counting.countExpected(lineId, quantity, null, false, AT);
    }

    private UUID productId(String code) {
        return barcodes.findByCode(code).orElseThrow().getProduct().getId();
    }

    private Long unitCostPaise(UUID lotId, String code) {
        return jdbc.queryForObject(
                "SELECT allocated_unit_cost_paise FROM batch WHERE lot_id = ? AND product_id = ?",
                Long.class, lotId.toString(), productId(code).toString());
    }

    private String costBasis(UUID lotId, String code) {
        return jdbc.queryForObject(
                "SELECT cost_basis FROM batch WHERE lot_id = ? AND product_id = ?",
                String.class, lotId.toString(), productId(code).toString());
    }

    private void runV37() throws Exception {
        String sql = new String(
                getClass().getResourceAsStream(
                        "/db/migration/V37__pin_cost_at_receipt_backfill.sql").readAllBytes(),
                UTF_8);
        jdbc.execute(sql);
    }

    @Test
    @DisplayName("Backfill re-pins uncosted stock to exactly what the counting path costs it at")
    void backfillMatchesTheLivePath() throws Exception {
        // A returns lot bought at a quarter of its stated value: rate 0.25. The counting path
        // pins the scaled cost as the stock is counted in.
        UUID lot = importLot("KITCHEN", 50_000, List.of( // paid 50,000 of 200,000 stated
                line("BOX-1", "PRICEY", 1, 120_000),
                line("BOX-1", "CHEAP", 8, 10_000)));
        count(lot, "PRICEY", 1);
        count(lot, "CHEAP", 8);
        em.flush();

        long livePricey = unitCostPaise(lot, "PRICEY");
        long liveCheap = unitCostPaise(lot, "CHEAP");
        assertThat(livePricey).isEqualTo(30_000); // 120,000 × 0.25
        assertThat(liveCheap).isEqualTo(2_500); //   10,000 × 0.25

        // Strip both batches back to how the old code left them: counted, but costed at nothing.
        jdbc.update(
                "UPDATE batch SET allocated_unit_cost_paise = NULL, allocated_total_paise = 0,"
                        + " cost_basis = NULL WHERE lot_id = ?",
                lot.toString());
        assertThat(unitCostPaise(lot, "PRICEY")).isNull();

        runV37();

        assertThat(unitCostPaise(lot, "PRICEY"))
                .as("the migration reproduces the live cost, not a drift")
                .isEqualTo(livePricey);
        assertThat(unitCostPaise(lot, "CHEAP")).isEqualTo(liveCheap);
        assertThat(costBasis(lot, "PRICEY")).isEqualTo("PINNED");
        assertThat(jdbc.queryForObject(
                        "SELECT allocated_total_paise FROM batch WHERE lot_id = ? AND product_id = ?",
                        Long.class, lot.toString(), productId("PRICEY").toString()))
                .isEqualTo(30_000); // unit × one sellable unit
    }

    @Test
    @DisplayName("Backfill leaves goods that state no value uncosted, and does not re-touch costed stock")
    void backfillSkipsValuelessAndCosted() throws Exception {
        UUID valueless = importLot("KITCHEN", 100_000, List.of(line("BOX-9", "NOVALUE", 2, 0)));
        count(valueless, "NOVALUE", 2);

        UUID costed = importLot("KITCHEN", 40_000, List.of(line("BOX-8", "COSTED", 4, 10_000)));
        count(costed, "COSTED", 4);
        em.flush();
        long before = unitCostPaise(costed, "COSTED"); // 10,000 at rate 1.0

        runV37();

        assertThat(unitCostPaise(valueless, "NOVALUE"))
                .as("a lot that states no value cannot be costed and is left alone")
                .isNull();
        assertThat(unitCostPaise(costed, "COSTED"))
                .as("stock already costed is not disturbed")
                .isEqualTo(before);
    }
}
