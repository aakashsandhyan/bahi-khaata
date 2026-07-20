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
    @Column(name = "allocated_total_paise", nullable = false)
    private Money allocatedTotal;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "allocated_unit_cost_paise", nullable = false)
    private Money allocatedUnitCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_basis", nullable = false, columnDefinition = "text")
    private CostBasis costBasis;

    @Column(name = "quantity_received", nullable = false)
    private long quantityReceived;

    @Column(name = "quantity_damaged", nullable = false)
    private long quantityDamaged;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "mrp_paise", nullable = false)
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
        this.mrp = Objects.requireNonNull(mrp, "mrp");
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

    public Money getMrp() {
        return mrp;
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
