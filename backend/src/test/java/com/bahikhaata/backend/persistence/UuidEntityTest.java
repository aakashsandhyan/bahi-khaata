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
package com.bahikhaata.backend.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Identity semantics only — no persistence. The mapping itself (UUID field against a
 * {@code CHAR(36)} column) is exercised for real by the first entity in section 2.
 *
 * <p>The fixtures below deliberately carry no {@code @Entity} annotation. Hibernate scans
 * the classpath for entities, and a test-only entity would be picked up by every other
 * test's context and then fail validation against a table that does not exist.
 */
class UuidEntityTest {

    private static final class Product extends UuidEntity {
        Product() {
            super(newId());
        }

        Product(UUID id) {
            super(id);
        }
    }

    private static final class Invoice extends UuidEntity {
        Invoice(UUID id) {
            super(id);
        }
    }

    @Test
    @DisplayName("An identifier is assigned at construction, before any persistence")
    void identifierAssignedAtConstruction() {
        Product product = new Product();

        assertThat(product.getId()).isNotNull();
        assertThat(product.getId().version()).isEqualTo(4);
    }

    @Test
    void distinctInstancesGetDistinctIdentifiers() {
        assertThat(new Product().getId()).isNotEqualTo(new Product().getId());
    }

    @Test
    @DisplayName("Same type and same identifier is the same entity")
    void equalityIsTypeAndIdentifier() {
        UUID shared = UUID.randomUUID();

        assertThat(new Product(shared)).isEqualTo(new Product(shared));
        assertThat(new Product(shared)).hasSameHashCodeAs(new Product(shared));
    }

    @Test
    @DisplayName("The same identifier on a different entity type is not the same entity")
    void differentTypesAreNeverEqual() {
        UUID shared = UUID.randomUUID();

        // Nothing stops two tables holding the same UUID. Comparing ids alone would
        // make an invoice equal a product.
        assertThat(new Product(shared)).isNotEqualTo(new Invoice(shared));
    }

    @Test
    void behavesInHashBasedCollections() {
        UUID shared = UUID.randomUUID();
        Set<UuidEntity> set = new HashSet<>();

        set.add(new Product(shared));
        set.add(new Product(shared));
        set.add(new Product());

        assertThat(set).hasSize(2);
    }

    @Test
    void nullIdentifierIsRefused() {
        assertThatThrownBy(() -> new Product(null)).isInstanceOf(NullPointerException.class);
    }
}
