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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Proves that {@code @Immutable} stops Hibernate's dirty checking from ever emitting an UPDATE.
 * The field is deliberately mutable in Java so the test can change it and confirm Hibernate
 * ignores the change — that is the whole point of the check.
 */
@Entity
@Immutable
public class LedgerRow {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "movement_type", nullable = false)
    private String movementType;

    protected LedgerRow() {
        // for Hibernate
    }

    public LedgerRow(long quantity, String movementType) {
        this.id = UUID.randomUUID().toString();
        this.quantity = quantity;
        this.movementType = movementType;
    }

    public String getId() {
        return id;
    }

    public long getQuantity() {
        return quantity;
    }

    public String getMovementType() {
        return movementType;
    }

    /** Only exists so the test can attempt a mutation Hibernate must refuse to persist. */
    void attemptMutation(long newQuantity) {
        this.quantity = newQuantity;
    }
}
