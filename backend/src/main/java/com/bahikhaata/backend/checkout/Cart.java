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
package com.bahikhaata.backend.checkout;

import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A sale being rung up. Mutable working state — the scratch paper the till writes on until the
 * customer pays, at which point it becomes an immutable invoice. Lines change freely here;
 * nothing about a cart is a record until it is paid.
 */
@Entity
@Table(name = "cart")
public class Cart extends UuidEntity {

    @Column(name = "state", nullable = false, columnDefinition = "text")
    private String state = "OPEN";

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    public Cart() {
        super(newId());
    }

    public String getState() {
        return state;
    }

    public boolean isOpen() {
        return "OPEN".equals(state);
    }
}
