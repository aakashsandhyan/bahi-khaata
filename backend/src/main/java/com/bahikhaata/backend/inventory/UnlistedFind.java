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
import com.bahikhaata.backend.persistence.UuidEntity;
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
 * Goods found in a carton that no line names.
 *
 * <p>Kept apart from {@link ExpectedLine} deliberately: that table records what the supplier
 * claimed, and writing our own discoveries into it would blur the single distinction this
 * whole change exists to preserve — between what was promised and what arrived.
 *
 * <p>These still take a share of the lot when it closes, because the money bought whatever
 * turned up. Having no stated value of their own, they are weighed at the lot's average unit
 * value and recorded as an estimate.
 */
@Entity
@Table(name = "unlisted_find")
public class UnlistedFind extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "box_id", nullable = false)
    private Box box;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    /** For Hibernate. */
    protected UnlistedFind() {}

    public UnlistedFind(Lot lot, Box box, Product product, long quantity) {
        super(newId());
        this.lot = Objects.requireNonNull(lot, "lot");
        this.box = Objects.requireNonNull(box, "box");
        this.product = Objects.requireNonNull(product, "product");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + quantity);
        }
        this.quantity = quantity;
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

    public long getQuantity() {
        return quantity;
    }

    public void add(long more) {
        if (more <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + more);
        }
        this.quantity += more;
    }

    /** Taken down when some of the extra is reattributed to a real product it turned out to be. */
    public void reduce(long fewer) {
        if (fewer <= 0 || fewer > quantity) {
            throw new IllegalArgumentException(
                    "cannot reduce " + quantity + " extra by " + fewer);
        }
        this.quantity -= fewer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
