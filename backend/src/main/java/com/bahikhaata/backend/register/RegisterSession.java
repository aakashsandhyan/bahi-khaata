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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One drawer's accountable stretch: opened with a counted float by a named operator, closed against
 * a counted drawer. The over/short figure is computed once at close and <em>pinned</em> — like a
 * bill, it must survive later data corrections, so it is stored, never re-derived.
 */
@Entity
@Table(name = "register_session")
public class RegisterSession extends UuidEntity {

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @Column(name = "register_name", nullable = false, columnDefinition = "text")
    private String registerName;

    @Column(name = "operator_name", nullable = false, columnDefinition = "text")
    private String operatorName;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "float_paise", nullable = false)
    private Money floatAmount;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status;

    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "opened_at", nullable = false, columnDefinition = "text")
    private Instant openedAt;

    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "closed_at", columnDefinition = "text")
    private Instant closedAt;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "counted_paise")
    private Money counted;

    /** Signed paise: positive = drawer over, negative = short. Pinned at close. */
    @Column(name = "over_short_paise")
    private Long overShortPaise;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    protected RegisterSession() {}

    private RegisterSession(String registerName, String operatorName, Money floatAmount, Instant openedAt) {
        super(newId());
        this.registerName = registerName;
        this.operatorName = operatorName;
        this.floatAmount = floatAmount;
        this.status = OPEN;
        this.openedAt = openedAt;
    }

    public static RegisterSession open(String registerName, String operatorName, Money floatAmount, Instant at) {
        return new RegisterSession(registerName, operatorName, floatAmount, at);
    }

    /** Closes the session, pinning the counted drawer and the over/short it implies. */
    public void close(Money counted, long overShortPaise, Instant at) {
        if (!isOpen()) {
            throw new IllegalStateException("session is already closed");
        }
        this.counted = counted;
        this.overShortPaise = overShortPaise;
        this.closedAt = at;
        this.status = CLOSED;
    }

    public boolean isOpen() {
        return OPEN.equals(status);
    }

    public String getRegisterName() {
        return registerName;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public Money getFloatAmount() {
        return floatAmount;
    }

    public String getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Money getCounted() {
        return counted;
    }

    public Long getOverShortPaise() {
        return overShortPaise;
    }
}
