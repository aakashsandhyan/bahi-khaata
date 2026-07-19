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
package com.bahikhaata.spike;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task 1.1 — the gate. Each test corresponds to one thing the design depends on.
 * That the context starts at all proves Flyway-then-validate works, since
 * ddl-auto=validate aborts startup on any mapping mismatch.
 */
@SpringBootTest
class DialectSpikeTest {

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("Flyway applies migrations and Hibernate validates against them")
    void contextStartsWithFlywayThenValidate() {
        // Reaching this line means the context started, which with
        // ddl-auto=validate means Hibernate matched every mapping to the
        // schema Flyway created. There is nothing further to assert.
        assertThat(em).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("JSON attributes round-trip unchanged through a CLOB column")
    void jsonAttributesRoundTrip() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("serialNumber", "SN-4471");
        attributes.put("warrantyMonths", 24);
        attributes.put("colour", "steel");

        Widget saved = new Widget("Electric kettle", attributes, 89900L);
        em.persist(saved);
        em.flush();
        em.clear();

        Widget found = em.find(Widget.class, saved.getId());
        assertThat(found.getAttributes())
                .containsEntry("serialNumber", "SN-4471")
                .containsEntry("warrantyMonths", 24)
                .containsEntry("colour", "steel");
    }

    @Test
    @Transactional
    @DisplayName("A product carrying unfamiliar attribute names needs no migration")
    void unfamiliarAttributesNeedNoMigration() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("fabricGsm", 180);
        attributes.put("neverSeenBefore", "arbitrary");

        Widget saved = new Widget("Cotton throw", attributes, 45000L);
        em.persist(saved);
        em.flush();
        em.clear();

        assertThat(em.find(Widget.class, saved.getId()).getAttributes())
                .containsEntry("fabricGsm", 180)
                .containsEntry("neverSeenBefore", "arbitrary");
    }

    @Test
    @Transactional
    @DisplayName("Money stored as integer paise round-trips exactly")
    void paiseRoundTripsExactly() {
        // 12,345,678.99 rupees. A double would not carry this exactly.
        long paise = 1234567899L;

        Widget saved = new Widget("Expensive thing", Map.of(), paise);
        em.persist(saved);
        em.flush();
        em.clear();

        assertThat(em.find(Widget.class, saved.getId()).getPricePaise()).isEqualTo(paise);
    }

    @Test
    @Transactional
    @DisplayName("@Immutable stops dirty checking from ever emitting an UPDATE")
    void immutableEntityIsNeverUpdated() {
        LedgerRow saved = new LedgerRow(5L, "SALE");
        em.persist(saved);
        em.flush();
        em.clear();

        LedgerRow loaded = em.find(LedgerRow.class, saved.getId());
        loaded.attemptMutation(999L);
        em.flush();
        em.clear();

        assertThat(em.find(LedgerRow.class, saved.getId()).getQuantity()).isEqualTo(5L);
    }
}
