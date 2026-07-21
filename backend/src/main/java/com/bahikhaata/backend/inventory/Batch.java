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

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.MoneyConverter;
import com.bahikhaata.backend.persistence.UuidEntity;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.Money;
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
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One product's arrival within a lot, at the cost allocated to it.
 *
 * <p>The same product arriving again creates a new batch rather than altering an existing
 * one — that is the whole reason batches exist, since liquidation stock routinely arrives at
 * different costs. Selling price stays on the product; only cost varies here.
 *
 * <p>Has no arrival date of its own: FIFO orders by the lot's {@code received_on}, so there
 * is no denormalised per-batch date that could drift out of step with the delivery record.
 */
@Entity
@Table(name = "batch")
public class Batch extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    /**
     * This line's whole share of the lot amount. Authoritative: the shares of a lot's batches
     * sum to what was paid, exactly. The unit cost below is derived from it and rounded down,
     * so multiplying that back out does not recover this figure — which is precisely why both
     * are stored.
     */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "allocated_total_paise")
    private Money allocatedTotal;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "allocated_unit_cost_paise")
    private Money allocatedUnitCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_basis", columnDefinition = "text")
    private CostBasis costBasis;

    @Column(name = "quantity_received", nullable = false)
    private long quantityReceived;

    @Column(name = "quantity_damaged", nullable = false)
    private long quantityDamaged;

    /**
     * The maximum retail price printed on the goods, or null until someone has read it off
     * them. A legal figure rather than a derived one — it caps what may lawfully be charged
     * — so it is never inferred from a selling price or a manifest.
     */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "mrp_paise")
    private Money mrp;

    @Column(name = "mrp_is_estimate", nullable = false)
    private boolean mrpIsEstimate;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    /** For Hibernate. */
    protected Batch() {}

    /**
     * Stock that has been counted but not yet costed.
     *
     * <p>The normal state between someone opening a box and the lot being closed. A lot's
     * shares depend on every line in it, so none can be settled while a box is still unopened
     * — which means there is a real interval where goods are genuinely held at an unknown
     * cost, and saying so is more honest than inventing a figure.
     *
     * <p>The cost is left null rather than zero. Zero would be a lie the reports believe:
     * every margin computed from it comes out as pure profit.
     */
    public static Batch counted(
            Product product, Lot lot, long quantityCounted, Money mrp, boolean mrpIsEstimate) {
        return new Batch(product, lot, quantityCounted, mrp, mrpIsEstimate);
    }

    /** Uncosted stock. Reached through {@link #counted}, which says what it is for. */
    private Batch(
            Product product, Lot lot, long quantityCounted, Money mrp, boolean mrpIsEstimate) {
        super(newId());
        this.product = Objects.requireNonNull(product, "product");
        this.lot = Objects.requireNonNull(lot, "lot");
        if (quantityCounted <= 0) {
            throw new IllegalArgumentException(
                    "counted quantity must be positive, was " + quantityCounted);
        }
        this.quantityReceived = quantityCounted;
        this.quantityDamaged = 0;
        this.mrp = mrp;
        this.mrpIsEstimate = mrpIsEstimate;
    }

    /**
     * As below, deriving the line total from the unit cost. For batches not produced by an
     * allocation — test fixtures, and stock entered before allocation existed — where no
     * separate share figure exists to preserve.
     */
    public Batch(
            Product product,
            Lot lot,
            Money allocatedUnitCost,
            CostBasis costBasis,
            long quantityReceived,
            long quantityDamaged,
            Money mrp,
            boolean mrpIsEstimate) {
        this(
                product,
                lot,
                allocatedUnitCost.times(quantityReceived - quantityDamaged),
                allocatedUnitCost,
                costBasis,
                quantityReceived,
                quantityDamaged,
                mrp,
                mrpIsEstimate);
    }

    public Batch(
            Product product,
            Lot lot,
            Money allocatedTotal,
            Money allocatedUnitCost,
            CostBasis costBasis,
            long quantityReceived,
            long quantityDamaged,
            Money mrp,
            boolean mrpIsEstimate) {
        super(newId());
        this.allocatedTotal = Objects.requireNonNull(allocatedTotal, "allocatedTotal");
        this.product = Objects.requireNonNull(product, "product");
        this.lot = Objects.requireNonNull(lot, "lot");
        this.allocatedUnitCost = Objects.requireNonNull(allocatedUnitCost, "allocatedUnitCost");
        this.costBasis = Objects.requireNonNull(costBasis, "costBasis");
        this.mrp = mrp;
        if (quantityReceived <= 0) {
            throw new IllegalArgumentException("quantity received must be positive");
        }
        if (quantityDamaged < 0 || quantityDamaged > quantityReceived) {
            throw new IllegalArgumentException(
                    "quantity damaged must be between zero and the quantity received");
        }
        this.quantityReceived = quantityReceived;
        this.quantityDamaged = quantityDamaged;
        this.mrpIsEstimate = mrpIsEstimate;
    }

    /**
     * The units that can actually be sold. Damaged units are excluded from the divisor when
     * cost is allocated, so their share is absorbed by these — the full lot was paid for
     * regardless of how much of it earns.
     */
    public long sellableQuantity() {
        return quantityReceived - quantityDamaged;
    }

    public Product getProduct() {
        return product;
    }

    public Lot getLot() {
        return lot;
    }

    /** This line's share of the lot amount — the figure that reconciles. */
    /**
     * Whether this batch's share of the lot has been settled.
     *
     * <p>False while its lot is still being unpacked. Callers that read a cost must check
     * this first: an uncosted batch answers null, not zero, and code that treats the two the
     * same reports a margin of 100%.
     */
    public boolean isCosted() {
        return allocatedTotal != null;
    }

    /**
     * Records this batch's share of the lot amount, when the lot is closed.
     *
     * <p>Once only. A batch's cost may already have been used to set a price and to record
     * cost of goods sold, so overwriting it would rewrite margin history.
     */
    public void applyAllocation(Money total, Money unitCost, CostBasis basis) {
        if (isCosted()) {
            throw new IllegalStateException(
                    "batch " + getId() + " is already costed at " + allocatedTotal);
        }
        this.allocatedTotal = Objects.requireNonNull(total, "allocated total");
        this.allocatedUnitCost = Objects.requireNonNull(unitCost, "allocated unit cost");
        this.costBasis = Objects.requireNonNull(basis, "cost basis");
    }

    /** Adds to what has been counted, as more of the same line is found in a box. */
    public void addCounted(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("counted quantity must be positive, was " + quantity);
        }
        if (isCosted()) {
            throw new IllegalStateException(
                    "batch " + getId() + " is already costed; its lot has been closed");
        }
        this.quantityReceived += quantity;
    }

    /** This line's share of the lot amount, or null while its lot is still open. */
    public Money getAllocatedTotal() {
        return allocatedTotal;
    }

    /** The share divided by the sellable quantity, rounded down. What COGS uses. */
    public Money getAllocatedUnitCost() {
        return allocatedUnitCost;
    }

    public CostBasis getCostBasis() {
        return costBasis;
    }

    public long getQuantityReceived() {
        return quantityReceived;
    }

    public long getQuantityDamaged() {
        return quantityDamaged;
    }

    /** The printed maximum retail price, or null if it has not been recorded yet. */
    public Money getMrp() {
        return mrp;
    }

    /** Records the MRP read off the goods. */
    public void recordMrp(Money printedMrp, boolean isEstimate) {
        Objects.requireNonNull(printedMrp, "mrp");
        if (!printedMrp.isPositive()) {
            throw new IllegalArgumentException("an MRP must be positive, was " + printedMrp);
        }
        this.mrp = printedMrp;
        this.mrpIsEstimate = isEstimate;
    }

    public boolean isMrpEstimate() {
        return mrpIsEstimate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
