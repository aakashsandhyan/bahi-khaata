/*
 * bahi-khaata — point of sale for Bachat Baazar
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
package com.bahikhaata.backend.inventory;

import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.LocalDateIso8601Converter;
import com.bahikhaata.backend.persistence.MoneyConverter;
import com.bahikhaata.backend.persistence.UuidEntity;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.LotState;
import com.bahikhaata.contracts.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One purchase: a pallet bought for a single sum.
 *
 * <p>The lot amount is what was actually paid and is the auditable figure. Per-product costs
 * are derived from it by allocation and must always reconcile back to it — never entered
 * directly, except where a line is pinned.
 *
 * <p>Editable while no stock from it has been consumed, frozen thereafter, because by then
 * its allocated costs have been used to record cost of goods sold and changing them would
 * rewrite margin history. That rule is enforced in application logic, not by a trigger, since
 * the condition depends on the ledger rather than on this row.
 */
@Entity
@Table(name = "lot")
public class Lot extends UuidEntity {

    @Column(name = "supplier", nullable = false, columnDefinition = "text")
    private String supplier;

    /**
     * The business delivery date, not a system stamp. FIFO consumes by this, so a delivery
     * logged late still consumes in true arrival order.
     */
    @Convert(converter = LocalDateIso8601Converter.class)
    @Column(name = "received_on", nullable = false, columnDefinition = "text")
    private LocalDate receivedOn;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "amount_paid_paise", nullable = false)
    private Money amountPaid;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "freight_paise", nullable = false)
    private Money freight;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_method", nullable = false, columnDefinition = "text")
    private AllocationMethod allocationMethod;

    /**
     * Open while boxes are still being counted; closed once the amount paid has been split
     * across what actually arrived.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, columnDefinition = "text")
    private LotState state = LotState.OPEN;

    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "closed_at", columnDefinition = "text")
    private Instant closedAt;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    /** For Hibernate. */
    protected Lot() {}

    public Lot(
            String supplier,
            LocalDate receivedOn,
            Money amountPaid,
            Money freight,
            AllocationMethod allocationMethod) {
        super(newId());
        this.supplier = Objects.requireNonNull(supplier, "supplier");
        this.receivedOn = Objects.requireNonNull(receivedOn, "receivedOn");
        this.amountPaid = Objects.requireNonNull(amountPaid, "amountPaid");
        this.freight = Objects.requireNonNull(freight, "freight");
        this.allocationMethod = Objects.requireNonNull(allocationMethod, "allocationMethod");
    }

    /**
     * The amount spread across this lot's batches: what was paid plus the freight to get it
     * here. Freight is part of landed cost, so leaving it out would understate every unit.
     */
    public Money totalToAllocate() {
        return amountPaid.plus(freight);
    }

    public String getSupplier() {
        return supplier;
    }

    public LocalDate getReceivedOn() {
        return receivedOn;
    }

    public Money getAmountPaid() {
        return amountPaid;
    }

    public Money getFreight() {
        return freight;
    }

    public AllocationMethod getAllocationMethod() {
        return allocationMethod;
    }

    public LotState getState() {
        return state;
    }

    public boolean isOpen() {
        return state == LotState.OPEN;
    }

    /** When unpacking finished and the cost was apportioned, or null while still open. */
    public Instant getClosedAt() {
        return closedAt;
    }

    /**
     * Marks this lot closed at the given moment.
     *
     * <p>One-way: a lot that has been closed cannot be reopened here, because its apportioned
     * costs may already have been used to set prices, and quietly changing them would leave
     * those prices resting on figures that no longer exist. Correcting a closed lot is a
     * deliberate, recorded act elsewhere, not a side effect of scanning something.
     */
    public void close(Instant at) {
        Objects.requireNonNull(at, "closing time");
        if (state == LotState.CLOSED) {
            throw new IllegalStateException(
                    "lot " + getId() + " was already closed at " + closedAt);
        }
        this.state = LotState.CLOSED;
        this.closedAt = at;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
