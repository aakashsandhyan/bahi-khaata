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
import com.bahikhaata.contracts.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One line of what a supplier says is coming.
 *
 * <p>A claim, not a holding. Deliberately separate from {@link Batch}, which is stock we
 * actually have: keeping the claim after counting is the whole point, because it is what a
 * shortfall is measured against. Deleting it once counted would destroy the only record that
 * twelve were promised where eleven arrived.
 */
@Entity
@Table(name = "expected_line")
public class ExpectedLine extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "box_id", nullable = false)
    private Box box;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "code", nullable = false, columnDefinition = "text")
    private String code;

    @Column(name = "quantity_expected", nullable = false)
    private long quantityExpected;

    /**
     * How many have actually been found so far. Kept beside the expectation rather than
     * replacing it, because the difference between them is the thing worth recording.
     *
     * <p>Held per line rather than derived from the batch: a batch sums a product across every
     * carton it arrives in, so it cannot say what is left to find in this particular box —
     * which is what someone resuming a part-counted carton needs to see.
     */
    @Column(name = "quantity_counted", nullable = false)
    private long quantityCounted;

    /**
     * What the line is worth per unit, used to weigh its share of the lot: a marketplace
     * selling price on a returns sheet, a supplier cost on a cost-plus one.
     *
     * <p>Per unit, never a line total. Quantity is applied by the allocation itself, and a
     * total here would apply it twice — inflating multi-unit lines and squeezing single-unit
     * ones while leaving the lot total looking perfectly correct.
     *
     * <p>Null where the manifest states neither, in which case the line is weighed at the
     * lot's average unit value.
     */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "stated_value_paise")
    private Money statedValue;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    /** For Hibernate. */
    protected ExpectedLine() {}

    public ExpectedLine(
            Lot lot, Box box, Product product, String code, long quantityExpected,
            Money statedValue) {
        super(newId());
        this.lot = Objects.requireNonNull(lot, "lot");
        this.box = Objects.requireNonNull(box, "box");
        this.product = Objects.requireNonNull(product, "product");
        this.code = Objects.requireNonNull(code, "code");
        if (quantityExpected <= 0) {
            throw new IllegalArgumentException(
                    "expected quantity must be positive, was " + quantityExpected);
        }
        this.quantityExpected = quantityExpected;
        this.statedValue = statedValue;
    }

    public Lot getLot() {
        return lot;
    }

    public Box getBox() {
        return box;
    }

    public Product getProduct() {
        return product;
    }

    public String getCode() {
        return code;
    }

    public long getQuantityExpected() {
        return quantityExpected;
    }

    /** The per-unit weight for apportioning, or null if the manifest stated none. */
    public Money getStatedValue() {
        return statedValue;
    }

    /** Takes units back off, when a count was a mistake. Never below nothing. */
    public void removeCounted(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("must take off a positive number, was " + quantity);
        }
        this.quantityCounted = Math.max(0, this.quantityCounted - quantity);
    }

    public long getQuantityCounted() {
        return quantityCounted;
    }

    /** What is still to be found here, or zero once everything expected has turned up. */
    public long getQuantityOutstanding() {
        return Math.max(0, quantityExpected - quantityCounted);
    }

    /**
     * The difference between what was promised and what was found: negative when short,
     * positive when more turned up than the manifest claimed.
     */
    public long getDiscrepancy() {
        return quantityCounted - quantityExpected;
    }

    public boolean isFullyCounted() {
        return quantityCounted >= quantityExpected;
    }

    /**
     * Records units found. Additive, so a carton counted in several sittings accumulates
     * rather than overwriting — someone who counts four, goes home, and counts three more has
     * found seven.
     *
     * <p>Counting beyond the expectation is permitted: more turning up than was promised is a
     * fact about the delivery, and refusing to record it would force the operator to lie.
     */
    public void recordCounted(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("counted quantity must be positive, was " + quantity);
        }
        this.quantityCounted += quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
