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
package com.bahikhaata.backend.inventory;

import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.LocalDateIso8601Converter;
import com.bahikhaata.backend.persistence.MoneyConverter;
import com.bahikhaata.backend.persistence.UuidEntity;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.CostAnchor;
import com.bahikhaata.contracts.CostBasisStrategy;
import com.bahikhaata.contracts.LotState;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.MultiplierBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
     * The vendor this lot came from. Nullable at the mapping because SQLite cannot retrofit a
     * NOT NULL constraint without a table rebuild; in practice every lot has one — existing rows
     * were backfilled by migration V38, and new lots are only created through the receipt path,
     * which resolves an existing active supplier first. The legacy {@link #supplier} text above
     * is kept as a denormalised snapshot of this supplier's name at receipt time.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplierRef;

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

    @Column(name = "receiving_complete", nullable = false)
    private boolean receivingComplete = false;

    @Column(name = "is_manual", nullable = false)
    private boolean isManual = false;

    /**
     * The default category for products added to this lot. Optional — a lot created before
     * this field existed, or one whose operator has not chosen one, is {@code null} and falls
     * back to {@link LotCategoryResolver}'s derived value. Set through the setter, not a
     * constructor parameter, since it is unknown at receipt time for a manual lot with no
     * products yet, and {@code add-product} may still override it per line.
     */
    @Column(name = "category", columnDefinition = "text")
    private String category;

    /**
     * How this lot's products' cost is derived, or null for a lot that keeps today's behaviour
     * — the manifest rate, or the amount-paid apportionment. The strategy and its params
     * ({@link #costAnchor}, {@link #flatUnitCost}, {@link #percentBp}, {@link #multiplierMilli},
     * {@link #multiplierBase}) live here on the lot; a batch never records more than the plain
     * cost this basis derived for it ({@code CostBasis.PINNED}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cost_basis_strategy", columnDefinition = "text")
    private CostBasisStrategy costBasisStrategy;

    /** MRP or ASP — required by an anchor-dependent strategy, null for one that needs none. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cost_anchor", columnDefinition = "text")
    private CostAnchor costAnchor;

    /** FLAT_PER_UNIT's stated cost, and MULTIPLIER's base when it is an entered cost. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "flat_unit_cost_paise")
    private Money flatUnitCost;

    /** PERCENT_OF_ANCHOR's percentage, in basis points — 30% is stored as 3000. */
    @Column(name = "percent_bp")
    private Long percentBp;

    /** MULTIPLIER's factor, in milli-units — 1.25× is stored as 1250. */
    @Column(name = "multiplier_milli")
    private Long multiplierMilli;

    /** What MULTIPLIER multiplies: an entered cost, the anchor, or the manifest stated value. */
    @Enumerated(EnumType.STRING)
    @Column(name = "multiplier_base", columnDefinition = "text")
    private MultiplierBase multiplierBase;

    /** For Hibernate. */
    protected Lot() {}

    public Lot(
            String supplier,
            LocalDate receivedOn,
            Money amountPaid,
            Money freight,
            AllocationMethod allocationMethod) {
        this(supplier, receivedOn, amountPaid, freight, allocationMethod, false);
    }

    public Lot(
            String supplier,
            LocalDate receivedOn,
            Money amountPaid,
            Money freight,
            AllocationMethod allocationMethod,
            boolean isManual) {
        super(newId());
        this.supplier = Objects.requireNonNull(supplier, "supplier");
        this.receivedOn = Objects.requireNonNull(receivedOn, "receivedOn");
        this.amountPaid = Objects.requireNonNull(amountPaid, "amountPaid");
        this.freight = Objects.requireNonNull(freight, "freight");
        this.allocationMethod = Objects.requireNonNull(allocationMethod, "allocationMethod");
        this.isManual = isManual;
    }

    /** The receipt path's constructor: links the supplier entity and snapshots its name. */
    public Lot(
            Supplier supplierRef,
            LocalDate receivedOn,
            Money amountPaid,
            Money freight,
            AllocationMethod allocationMethod) {
        this(supplierRef, receivedOn, amountPaid, freight, allocationMethod, false);
    }

    public Lot(
            Supplier supplierRef,
            LocalDate receivedOn,
            Money amountPaid,
            Money freight,
            AllocationMethod allocationMethod,
            boolean isManual) {
        this(
                Objects.requireNonNull(supplierRef, "supplierRef").getName(),
                receivedOn,
                amountPaid,
                freight,
                allocationMethod,
                isManual);
        this.supplierRef = supplierRef;
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

    /** The linked supplier entity, or null for a legacy/test lot created before the link existed. */
    public Supplier getSupplierRef() {
        return supplierRef;
    }

    /**
     * Re-points this lot at a different supplier, refreshing the denormalised {@link #supplier}
     * name snapshot to match — the same pairing the constructor establishes, kept in sync here
     * so a corrected supplier is not left showing the old name.
     */
    public void setSupplierRef(Supplier supplierRef) {
        this.supplierRef = Objects.requireNonNull(supplierRef, "supplierRef");
        this.supplier = supplierRef.getName();
    }

    public LocalDate getReceivedOn() {
        return receivedOn;
    }

    public void setReceivedOn(LocalDate receivedOn) {
        this.receivedOn = Objects.requireNonNull(receivedOn, "receivedOn");
    }

    public Money getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(Money amountPaid) {
        this.amountPaid = Objects.requireNonNull(amountPaid, "amountPaid");
    }

    public Money getFreight() {
        return freight;
    }

    public void setFreight(Money freight) {
        this.freight = Objects.requireNonNull(freight, "freight");
    }

    public AllocationMethod getAllocationMethod() {
        return allocationMethod;
    }

    public void setAllocationMethod(AllocationMethod allocationMethod) {
        this.allocationMethod = Objects.requireNonNull(allocationMethod, "allocationMethod");
    }

    /** This lot's default category, or null if it has none set — see {@link #category}. */
    public String getCategory() {
        return category;
    }

    /** {@code null} leaves the lot without a default; the update path also accepts "" to clear it. */
    public void setCategory(String category) {
        this.category = category;
    }

    /** Whether this lot declares a cost basis at all — the fork between the two costing paths. */
    public boolean declaresCostBasis() {
        return costBasisStrategy != null;
    }

    public CostBasisStrategy getCostBasisStrategy() {
        return costBasisStrategy;
    }

    public void setCostBasisStrategy(CostBasisStrategy costBasisStrategy) {
        this.costBasisStrategy = costBasisStrategy;
    }

    public CostAnchor getCostAnchor() {
        return costAnchor;
    }

    public void setCostAnchor(CostAnchor costAnchor) {
        this.costAnchor = costAnchor;
    }

    public Money getFlatUnitCost() {
        return flatUnitCost;
    }

    public void setFlatUnitCost(Money flatUnitCost) {
        this.flatUnitCost = flatUnitCost;
    }

    public Long getPercentBp() {
        return percentBp;
    }

    public void setPercentBp(Long percentBp) {
        this.percentBp = percentBp;
    }

    public Long getMultiplierMilli() {
        return multiplierMilli;
    }

    public void setMultiplierMilli(Long multiplierMilli) {
        this.multiplierMilli = multiplierMilli;
    }

    public MultiplierBase getMultiplierBase() {
        return multiplierBase;
    }

    public void setMultiplierBase(MultiplierBase multiplierBase) {
        this.multiplierBase = multiplierBase;
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

    public boolean isReceivingComplete() {
        return receivingComplete;
    }

    public void setReceivingComplete(boolean receivingComplete) {
        this.receivingComplete = receivingComplete;
    }

    public boolean isManual() {
        return isManual;
    }

    public void setIsManual(boolean isManual) {
        this.isManual = isManual;
    }
}
