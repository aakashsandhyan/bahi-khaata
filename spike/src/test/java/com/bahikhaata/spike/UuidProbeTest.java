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
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class UuidProbeTest {

    @Autowired
    private EntityManager em;

    @Test
    @Transactional
    @DisplayName("A UUID-typed id round-trips and is stored as readable 36-character text")
    void uuidStoredAsReadableText() {
        UuidProbe saved = new UuidProbe("kettle");
        UUID assigned = saved.getId();
        em.persist(saved);
        em.flush();
        em.clear();

        assertThat(em.find(UuidProbe.class, assigned).getId()).isEqualTo(assigned);

        String raw = (String) em.createNativeQuery(
                        "SELECT id FROM uuid_probe WHERE label = 'kettle'")
                .getSingleResult();

        assertThat(raw).isEqualTo(assigned.toString()).hasSize(36);
    }
}
