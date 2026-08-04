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
package com.bahikhaata.backend.pricing;

import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A pricing-free product draft captured from a phone on the shop network.
 *
 * <p>Not a product and never on the shelf on its own: it holds a name and, optionally, an MRP, a
 * description, and a lot. A reviewer on the desktop completes lot/category/price and approves it,
 * which creates the real product (through the pricing workbench's manual save) and marks the
 * capture {@code approved}. A capture can also be {@code rejected}. Approval is the only path from
 * a capture to a shelf product.
 */
@Entity
@Table(name = "product_capture")
public class ProductCapture extends UuidEntity {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "mrp_paise")
    private Long mrpPaise;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    protected ProductCapture() {}

    public ProductCapture(String name, Long mrpPaise, String description, UUID lotId) {
        super(newId());
        this.name = Objects.requireNonNull(name, "name");
        this.mrpPaise = mrpPaise;
        this.description = description;
        this.lotId = lotId;
        this.status = PENDING;
    }

    public String getName() {
        return name;
    }

    public Long getMrpPaise() {
        return mrpPaise;
    }

    public String getDescription() {
        return description;
    }

    public UUID getLotId() {
        return lotId;
    }

    public String getStatus() {
        return status;
    }

    public boolean isPending() {
        return PENDING.equals(status);
    }

    /** Marks the capture approved, once a reviewer has turned it into a product. */
    public void approve() {
        requirePending();
        this.status = APPROVED;
    }

    /** Marks the capture rejected; no product is created. */
    public void reject() {
        requirePending();
        this.status = REJECTED;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("capture " + getId() + " is already " + status);
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
