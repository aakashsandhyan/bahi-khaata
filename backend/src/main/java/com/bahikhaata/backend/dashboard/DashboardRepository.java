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
package com.bahikhaata.backend.dashboard;

import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only aggregate queries backing the Dashboard screen, over tables several other packages
 * own ({@code sale}, {@code stock_ledger}, {@code batch}, {@code product}, {@code lot},
 * {@code product_capture}, {@code print_job}).
 *
 * <p>Plain {@link JdbcTemplate} rather than a Spring Data {@code JpaRepository}: every query here
 * spans tables that belong to no single entity, and each is a single-pass native aggregate — one
 * scan, not a row loaded per product — over an append-only ledger that only grows. A JpaRepository
 * interface would have to be declared against some arbitrary entity anyway, which would misstate
 * what this actually reads; {@link JdbcTemplate} is what {@link
 * com.bahikhaata.backend.health.HealthController} already reaches for when a query does not belong
 * to one entity's repository, and this is the same situation five tables over.
 */
@Repository
class DashboardRepository {

    private final JdbcTemplate jdbc;

    DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Revenue and bill count for sales whose {@code created_at} falls in {@code [startUtc, endUtc)}. */
    RevenueWindow revenueInWindow(String startUtc, String endUtc) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(total_paise), 0) AS total_paise, COUNT(*) AS bill_count "
                        + "FROM sale WHERE created_at >= ? AND created_at < ?",
                (rs, rowNum) -> new RevenueWindow(rs.getLong("total_paise"), rs.getLong("bill_count")),
                startUtc, endUtc);
    }

    /**
     * One pass over {@code stock_ledger} joined to {@code batch} and {@code product}, answering
     * both the received-vs-priced KPI and the received → priced → sold funnel at once: received
     * units (and their MRP value) come from {@code PURCHASE_RECEIPT} rows; on-floor priced units
     * (and their MRP value) are on-hand units — summed across every movement type, per D6/D3 — for
     * products carrying a {@code selling_price_paise}; the unpriced backlog is the same on-hand
     * sum for products that do not; sold units (and their MRP value) come from {@code SALE} rows.
     * Every figure is a MRP-weighted sum over the same joined rows, so this is one scan, not five.
     */
    LedgerStages ledgerStages() {
        return jdbc.queryForObject(
                "SELECT "
                        + "COALESCE(SUM(CASE WHEN sl.movement_type = 'PURCHASE_RECEIPT' THEN sl.quantity ELSE 0 END), 0) AS received_units, "
                        + "COALESCE(SUM(CASE WHEN sl.movement_type = 'PURCHASE_RECEIPT' THEN sl.quantity * b.mrp_paise ELSE 0 END), 0) AS received_mrp_paise, "
                        + "COALESCE(SUM(CASE WHEN p.selling_price_paise IS NOT NULL THEN sl.quantity ELSE 0 END), 0) AS priced_units, "
                        + "COALESCE(SUM(CASE WHEN p.selling_price_paise IS NOT NULL THEN sl.quantity * b.mrp_paise ELSE 0 END), 0) AS priced_mrp_paise, "
                        + "COALESCE(SUM(CASE WHEN p.selling_price_paise IS NULL THEN sl.quantity ELSE 0 END), 0) AS unpriced_backlog_units, "
                        + "COALESCE(SUM(CASE WHEN sl.movement_type = 'SALE' THEN -sl.quantity ELSE 0 END), 0) AS sold_units, "
                        + "COALESCE(SUM(CASE WHEN sl.movement_type = 'SALE' THEN -sl.quantity * b.mrp_paise ELSE 0 END), 0) AS sold_mrp_paise "
                        + "FROM stock_ledger sl "
                        + "JOIN batch b ON b.id = sl.batch_id "
                        + "JOIN product p ON p.id = sl.product_id",
                (rs, rowNum) -> new LedgerStages(
                        rs.getLong("received_units"), rs.getLong("received_mrp_paise"),
                        rs.getLong("priced_units"), rs.getLong("priced_mrp_paise"),
                        rs.getLong("unpriced_backlog_units"),
                        rs.getLong("sold_units"), rs.getLong("sold_mrp_paise")));
    }

    /** All-time revenue against all-time lot spend — the two figures the recovery ratio divides. */
    Recovery recovery() {
        return jdbc.queryForObject(
                "SELECT "
                        + "COALESCE((SELECT SUM(total_paise) FROM sale), 0) AS revenue_paise, "
                        + "COALESCE((SELECT SUM(amount_paid_paise) FROM lot), 0) AS paid_paise",
                (rs, rowNum) -> new Recovery(rs.getLong("revenue_paise"), rs.getLong("paid_paise")));
    }

    /** All-time GST collected — structurally zero until gst-inclusive-pricing ships. */
    long gstAllTimePaise() {
        Long total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(tax_paise), 0) FROM sale", Long.class);
        return total == null ? 0 : total;
    }

    /** Units held in {@code NEEDS_WORK} — off-ledger by design (see V25), so the ledger cannot answer this. */
    long needsWorkBacklogUnits() {
        Long total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity_received), 0) FROM batch WHERE condition = 'NEEDS_WORK'",
                Long.class);
        return total == null ? 0 : total;
    }

    /** Phone captures still waiting on a reviewer. */
    long pendingCaptureCount() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_capture WHERE status = 'pending'", Long.class);
        return count == null ? 0 : count;
    }

    /** Label print jobs held in the review queue — see the deviation noted in DashboardService. */
    long reviewPrintJobCount() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM print_job WHERE status = 'review'", Long.class);
        return count == null ? 0 : count;
    }

    /** Open lots (receiving not yet complete) whose business receipt date is before {@code staleBefore}. */
    long staleOpenLotCount(LocalDate staleBefore) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM lot WHERE receiving_complete = 0 AND received_on < ?",
                Long.class, staleBefore.toString());
        return count == null ? 0 : count;
    }

    record RevenueWindow(long totalPaise, long billCount) {}

    record LedgerStages(
            long receivedUnits, long receivedMrpPaise,
            long pricedUnits, long pricedMrpPaise,
            long unpricedBacklogUnits,
            long soldUnits, long soldMrpPaise) {}

    record Recovery(long revenuePaise, long paidPaise) {}
}
