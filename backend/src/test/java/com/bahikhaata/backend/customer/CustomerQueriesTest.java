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

import static org.assertj.core.api.Assertions.assertThat;

import com.bahikhaata.contracts.CustomerViews.CustomerStats;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The derived figures, proven against hand-planted sales: repeat share counts only 2+-visit
 * customers' revenue, averages split repeat vs walk-in, and lapsed is a 60-day boundary on the
 * LAST visit. Sales are inserted directly — these queries read tables, not services.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-customer-queries.db")
@Transactional
class CustomerQueriesTest {

    @Autowired private CustomerQueries queries;
    @Autowired private CustomerService customers;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private jakarta.persistence.EntityManager em;

    private void plantSale(UUID customerId, long totalPaise, Instant at) {
        // The customers were saved through JPA in this same transaction; JdbcTemplate writes
        // beneath the persistence context, so flush first or the FK sees no customer row yet.
        em.flush();
        jdbc.update(
                "INSERT INTO sale (id, bill_no, payment_method, subtotal_paise, saving_paise, "
                        + "tax_paise, cgst_paise, sgst_paise, taxable_paise, total_paise, "
                        + "customer_id, created_at, updated_at) "
                        + "VALUES (?, ?, 'CASH', ?, 0, 0, 0, 0, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(),
                jdbc.queryForObject("SELECT COALESCE(MAX(bill_no),0)+1 FROM sale", Long.class),
                totalPaise, totalPaise, totalPaise,
                customerId == null ? null : customerId.toString(),
                at.toString(), at.toString());
    }

    @Test
    void statsDeriveFromPlantedSales() {
        Instant now = Instant.now();
        UUID meera = customers.save("Meera", "9821455120").getId();   // 2 visits → repeat
        UUID rakesh = customers.save("Rakesh", "9910588214").getId(); // 1 visit → not repeat
        customers.save("Fresh", "9000000001");                        // never bought → not lapsed

        plantSale(meera, 100_000, now.minusSeconds(3600));
        plantSale(meera, 200_000, now.minusSeconds(1800));
        plantSale(rakesh, 100_000, now.minusSeconds(3600));
        plantSale(null, 100_000, now.minusSeconds(3600)); // walk-in

        CustomerStats stats = queries.stats();

        assertThat(stats.peopleOnFile()).isEqualTo(3);
        // repeat revenue 300,000 of 500,000 total → 60%
        assertThat(stats.repeatShareOfRevenuePercent()).isEqualTo(60);
        assertThat(stats.averageRepeatBasketPaise()).isEqualTo(150_000);
        assertThat(stats.averageWalkInBasketPaise()).isEqualTo(100_000);
        assertThat(stats.lapsedSixtyDaysPlus()).isZero();
    }

    @Test
    void lapsedIsASixtyDayBoundaryOnTheLastVisit() {
        Instant now = Instant.now();
        UUID gone = customers.save("Gone", "9821455121").getId();
        UUID back = customers.save("Back", "9821455122").getId();

        plantSale(gone, 50_000, now.minusSeconds(61L * 24 * 60 * 60));
        // An old visit does not lapse a customer whose LAST visit is recent.
        plantSale(back, 50_000, now.minusSeconds(61L * 24 * 60 * 60));
        plantSale(back, 50_000, now.minusSeconds(3600));

        assertThat(queries.stats().lapsedSixtyDaysPlus()).isEqualTo(1);
    }

    @Test
    void listRowsMaskAndCount() {
        UUID meera = customers.save("Meera", "9821455120").getId();
        plantSale(meera, 100_000, Instant.now().minusSeconds(60));

        var rows = queries.rows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).mobileMasked()).isEqualTo("•••• 5120");
        assertThat(rows.get(0).visits()).isEqualTo(1);
        assertThat(rows.get(0).spentPaise()).isEqualTo(100_000);
    }
}
