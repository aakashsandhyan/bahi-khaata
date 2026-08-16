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
package com.bahikhaata.backend.customer;

import com.bahikhaata.contracts.CustomerViews.CustomerDetail;
import com.bahikhaata.contracts.CustomerViews.CustomerRow;
import com.bahikhaata.contracts.CustomerViews.CustomerStats;
import com.bahikhaata.contracts.CustomerViews.CustomerVisit;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Customers screen's figures — every one derived from {@code customer × sale (× sale_line)}
 * at read time. Deliberately no stored aggregates: at this shop's scale the SQL is trivial, and
 * counters drift. "Repeat" means two or more visits; "lapsed" means a last visit older than 60
 * days (a customer with no sale yet is new, not lapsed).
 */
@Service
public class CustomerQueries {

    private static final DateTimeFormatter SINCE =
            DateTimeFormatter.ofPattern("MMMM uuuu").withZone(ZoneId.of("Asia/Kolkata"));

    private final JdbcTemplate jdbc;

    CustomerQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CustomerStats stats() {
        long people = queryLong("SELECT COUNT(*) FROM customer");
        long totalRevenue = queryLong("SELECT COALESCE(SUM(total_paise), 0) FROM sale");
        long repeatRevenue = queryLong(
                "SELECT COALESCE(SUM(total_paise), 0) FROM sale WHERE customer_id IN ("
                        + "SELECT customer_id FROM sale WHERE customer_id IS NOT NULL "
                        + "GROUP BY customer_id HAVING COUNT(*) >= 2)");
        Long avgRepeat = queryNullableLong(
                "SELECT CAST(AVG(total_paise) AS INTEGER) FROM sale WHERE customer_id IN ("
                        + "SELECT customer_id FROM sale WHERE customer_id IS NOT NULL "
                        + "GROUP BY customer_id HAVING COUNT(*) >= 2)");
        Long avgWalkIn = queryNullableLong(
                "SELECT CAST(AVG(total_paise) AS INTEGER) FROM sale WHERE customer_id IS NULL");
        long lapsed = queryLong(
                "SELECT COUNT(*) FROM (SELECT customer_id, MAX(created_at) AS last_at FROM sale "
                        + "WHERE customer_id IS NOT NULL GROUP BY customer_id) "
                        + "WHERE last_at < ?",
                Instant.now().minusSeconds(60L * 24 * 60 * 60).toString());
        Integer repeatShare =
                totalRevenue == 0 ? null : (int) Math.round(repeatRevenue * 100.0 / totalRevenue);
        return new CustomerStats(people, repeatShare, avgRepeat, avgWalkIn, lapsed);
    }

    /** Every customer as a list row, most recent visit first, mobile pre-masked. */
    @Transactional(readOnly = true)
    public List<CustomerRow> rows() {
        List<Map<String, Object>> raw = jdbc.queryForList(
                "SELECT c.id, c.name, c.mobile, c.created_at, "
                        + "COUNT(s.id) AS visits, COALESCE(SUM(s.total_paise), 0) AS spent, "
                        + "MAX(s.created_at) AS last_at "
                        + "FROM customer c LEFT JOIN sale s ON s.customer_id = c.id "
                        + "GROUP BY c.id, c.name, c.mobile, c.created_at "
                        + "ORDER BY last_at DESC NULLS LAST, c.created_at DESC");
        return raw.stream().map(r -> {
            UUID id = UUID.fromString((String) r.get("id"));
            long visits = ((Number) r.get("visits")).longValue();
            long spent = ((Number) r.get("spent")).longValue();
            String lastAt = (String) r.get("last_at");
            return new CustomerRow(
                    id,
                    (String) r.get("name"),
                    tag(visits, (String) r.get("created_at")),
                    mask((String) r.get("mobile")),
                    visits,
                    spent,
                    visits == 0 ? null : spent / visits,
                    likes(id),
                    lastAt == null ? null : Instant.parse(lastAt));
        }).toList();
    }

    @Transactional(readOnly = true)
    public CustomerDetail detail(Customer customer) {
        List<CustomerVisit> visits = jdbc.queryForList(
                        "SELECT bill_no, total_paise, created_at, id FROM sale "
                                + "WHERE customer_id = ? ORDER BY created_at DESC",
                        customer.getId().toString())
                .stream()
                .map(r -> new CustomerVisit(
                        ((Number) r.get("bill_no")).longValue(),
                        String.format("BB-%06d", ((Number) r.get("bill_no")).longValue()),
                        Instant.parse((String) r.get("created_at")),
                        visitSummary((String) r.get("id")),
                        ((Number) r.get("total_paise")).longValue()))
                .toList();
        return new CustomerDetail(
                customer.getId(), customer.getName(), customer.getMobile(),
                customer.getCreatedAt(), visits);
    }

    /** "Dinner set + 2 more" — the first line's name and how many lines rode along. */
    private String visitSummary(String saleId) {
        List<String> names = jdbc.queryForList(
                "SELECT name FROM sale_line WHERE sale_id = ? ORDER BY created_at", String.class, saleId);
        if (names.isEmpty()) {
            return "";
        }
        return names.size() == 1
                ? names.get(0)
                : names.get(0) + " + " + (names.size() - 1) + " more";
    }

    /** Top two categories by spend across the customer's product lines — custom lines carry none. */
    private String likes(UUID customerId) {
        List<String> top = jdbc.queryForList(
                "SELECT p.category FROM sale_line sl "
                        + "JOIN sale s ON s.id = sl.sale_id AND s.customer_id = ? "
                        + "JOIN product p ON p.id = sl.product_id "
                        + "GROUP BY p.category ORDER BY SUM(sl.line_total_paise) DESC LIMIT 2",
                String.class, customerId.toString());
        return String.join(", ", top);
    }

    /** The artifact's tag: how established this customer is, and since when. */
    private String tag(long visits, String createdAt) {
        String since = SINCE.format(Instant.parse(createdAt));
        if (visits >= 5) {
            return "Regular · since " + since;
        }
        if (visits >= 2) {
            return "Repeat · since " + since;
        }
        return "New · " + since;
    }

    /** The list's masking rule: last four digits only. */
    public static String mask(String mobile) {
        return "•••• " + mobile.substring(mobile.length() - 4);
    }

    private long queryLong(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    private Long queryNullableLong(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
