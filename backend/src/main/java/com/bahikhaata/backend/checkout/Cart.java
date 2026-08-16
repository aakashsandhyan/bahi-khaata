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

    /** The captured customer riding this cart, or null — a hold keeps the person (V53). */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "customer_id")
    private java.util.UUID customerId;

    /** The register this cart was opened on, for the carts panel's chip; null from the classic till. */
    @Column(name = "register_name", columnDefinition = "text")
    private String registerName;

    /** Bumped by every mutation — the panel's sort key and the sweep's clock. Line changes do not
     * touch this row on their own, so honesty here is explicit (see Checkout's touch discipline). */
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "touched_at", columnDefinition = "text")
    private Instant touchedAt;

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
        this.touchedAt = Instant.now();
    }

    public java.util.UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(java.util.UUID customerId) {
        this.customerId = customerId;
    }

    public String getRegisterName() {
        return registerName;
    }

    public void setRegisterName(String registerName) {
        this.registerName = registerName;
    }

    public Instant getTouchedAt() {
        return touchedAt;
    }

    /** Every cart mutation counts as a touch — the list order and the sweep both read this. */
    public void touch() {
        this.touchedAt = Instant.now();
    }

    /** The morning sweep's verdict for a cart left over from a previous day. */
    public void markAbandoned() {
        this.state = "ABANDONED";
    }

    public String getState() {
        return state;
    }

    public boolean isOpen() {
        return "OPEN".equals(state);
    }

    /** Marks the cart paid once its sale is recorded, so it cannot be completed a second time. */
    public void markPaid() {
        this.state = "PAID";
    }
}
