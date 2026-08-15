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
package com.bahikhaata.backend.register;

import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.MoneyConverter;
import com.bahikhaata.backend.persistence.UuidEntity;
import com.bahikhaata.contracts.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Cash put into or taken out of an open drawer outside a sale — a supplier paid from the till, a
 * change top-up. Feeds the close-time expected-cash figure alongside the float and cash sales.
 */
@Entity
@Table(name = "register_cash_movement")
public class RegisterCashMovement extends UuidEntity {

    public static final String IN = "IN";
    public static final String OUT = "OUT";

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "direction", nullable = false, columnDefinition = "text")
    private String direction;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "amount_paise", nullable = false)
    private Money amount;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    protected RegisterCashMovement() {}

    public RegisterCashMovement(UUID sessionId, String direction, Money amount, String note) {
        super(newId());
        this.sessionId = sessionId;
        this.direction = direction;
        this.amount = amount;
        this.note = note;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getDirection() {
        return direction;
    }

    public Money getAmount() {
        return amount;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
