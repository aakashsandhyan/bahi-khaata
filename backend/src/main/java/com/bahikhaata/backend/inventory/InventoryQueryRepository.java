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
package com.bahikhaata.backend.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only aggregate queries backing the Inventory screen, over {@code stock_ledger} joined to
 * {@code batch}, {@code product}, and {@code lot} — the same {@link JdbcTemplate} pattern {@code
 * com.bahikhaata.backend.dashboard.DashboardRepository} already uses (design decision D2 of
 * palletworks-inventory), and for the same reason: this spans tables no single entity owns, and
 * must be one scan, not a row loaded per product over a ledger that only grows.
 *
 * <p>Cost is returned as a value/quantity pair rather than pre-divided, so the caller decides
 * rounding and — the point that matters — can tell an honestly-unknown cost apart from a real
 * zero. {@code uncostedRows} counts how many of a group's contributing ledger rows come from a
 * batch with no allocated cost yet: SQL's {@code SUM} silently skips {@code NULL} terms rather
 * than propagating them, so without this flag a partly-uncosted group would report a real-looking
 * but wrong low cost instead of an honest "not fully known" (mirrors {@link
 * Batch#isCosted}'s own reasoning — a margin computed against a false zero reads as pure profit).
 */
@Repository
class InventoryQueryRepository {

    private final JdbcTemplate jdbc;

    InventoryQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One row per product x stock condition, rolled up across lots (design decision D1): on-hand
     * quantity, a lot rollup (count plus, when there is exactly one, its identity), every distinct
     * bin, cost value/quantity, and the price this condition sells at. A group whose net on-hand is
     * not greater than zero never appears — the {@code HAVING} clause, not a Java-side filter.
     */
    List<AggregateRow> aggregateRows() {
        return jdbc.query(
                "SELECT p.id AS product_id, p.name AS product_name, p.category AS category_code, "
                        + "b.condition AS condition, "
                        + "SUM(sl.quantity) AS on_hand, "
                        + "COUNT(DISTINCT b.lot_id) AS lot_count, "
                        + "MIN(l.supplier) AS lot_supplier, MIN(l.received_on) AS lot_received_on, "
                        + "GROUP_CONCAT(DISTINCT b.bin) AS bins, "
                        + "COALESCE(SUM(sl.quantity * b.allocated_unit_cost_paise), 0) AS cost_value_paise, "
                        + "COALESCE(SUM(CASE WHEN b.allocated_unit_cost_paise IS NULL THEN 1 ELSE 0 END), 0) "
                        + "  AS uncosted_rows, "
                        + "MIN(CASE WHEN b.condition = 'DAMAGED' THEN p.damaged_selling_price_paise "
                        + "         ELSE p.selling_price_paise END) AS price_paise, "
                        + "fr.first_effective_at AS first_receipt_at "
                        + "FROM stock_ledger sl "
                        + "JOIN batch b ON b.id = sl.batch_id "
                        + "JOIN product p ON p.id = sl.product_id "
                        + "LEFT JOIN lot l ON l.id = b.lot_id "
                        + "LEFT JOIN ("
                        + "  SELECT product_id, MIN(effective_at) AS first_effective_at FROM stock_ledger "
                        + "  WHERE movement_type = 'PURCHASE_RECEIPT' GROUP BY product_id"
                        + ") fr ON fr.product_id = p.id "
                        + "GROUP BY p.id, b.condition "
                        + "HAVING SUM(sl.quantity) > 0 "
                        + "ORDER BY p.name, b.condition",
                (rs, rowNum) -> {
                    // getLong + wasNull, not getObject(..., Long.class) or a raw (Long) cast:
                    // SQLite's JDBC driver hands back whichever native numeric type fits a small
                    // value, so a bare cast throws, and its getObject(col, Long.class) refuses to
                    // convert. wasNull() reports on the most recent read, so it must be captured
                    // here, before any other column is touched.
                    long price = rs.getLong("price_paise");
                    boolean priceNull = rs.wasNull();
                    return new AggregateRow(
                            UUID.fromString(rs.getString("product_id")),
                            rs.getString("product_name"),
                            rs.getString("category_code"),
                            rs.getString("condition"),
                            rs.getLong("on_hand"),
                            rs.getInt("lot_count"),
                            rs.getString("lot_supplier"),
                            rs.getString("lot_received_on"),
                            rs.getString("bins"),
                            rs.getLong("cost_value_paise"),
                            rs.getLong("uncosted_rows"),
                            priceNull ? null : price,
                            rs.getString("first_receipt_at"));
                });
    }

    /**
     * On-hand cost value, quantity, and uncosted-row count for one product in one condition —
     * the item detail KPI cells' headline cost basis (scoped to GOOD stock; see {@code
     * InventoryService}).
     */
    CostSummary costSummary(UUID productId, String condition) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(sl.quantity * b.allocated_unit_cost_paise), 0) AS value_paise, "
                        + "COALESCE(SUM(sl.quantity), 0) AS qty, "
                        + "COALESCE(SUM(CASE WHEN b.allocated_unit_cost_paise IS NULL THEN 1 ELSE 0 END), 0) "
                        + "  AS uncosted_rows "
                        + "FROM stock_ledger sl JOIN batch b ON b.id = sl.batch_id "
                        + "WHERE sl.product_id = ? AND b.condition = ?",
                (rs, rowNum) ->
                        new CostSummary(
                                rs.getLong("value_paise"), rs.getLong("qty"), rs.getLong("uncosted_rows")),
                productId.toString(), condition);
    }

    record AggregateRow(
            UUID productId,
            String productName,
            String categoryCode,
            String condition,
            long onHand,
            int lotCount,
            String lotSupplier,
            String lotReceivedOn,
            String bins,
            long costValuePaise,
            long uncostedRows,
            Long pricePaise,
            String firstReceiptAt) {}

    record CostSummary(long valuePaise, long quantity, long uncostedRows) {}
}
