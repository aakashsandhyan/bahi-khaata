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
package com.bahikhaata.backend.catalog;

import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.UuidEntity;
import com.bahikhaata.contracts.Origin;
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

/**
 * A scannable code that resolves to one product.
 *
 * <p>A product may carry more than one — a manufacturer code and an internally generated one
 * — but a code belongs to a single product, enforced by the unique constraint on
 * {@code code}. Barcodes are assigned once and not edited, so unlike Product there is no
 * {@code updated_at}.
 */
@Entity
@Table(name = "barcode")
public class Barcode extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "code", nullable = false, unique = true, columnDefinition = "text")
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, columnDefinition = "text")
    private Origin origin;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    /** For Hibernate. */
    protected Barcode() {}

    public Barcode(Product product, String code, Origin origin) {
        super(newId());
        this.product = Objects.requireNonNull(product, "product");
        this.code = Objects.requireNonNull(code, "code");
        this.origin = Objects.requireNonNull(origin, "origin");
    }

    public Product getProduct() {
        return product;
    }

    /**
     * Points this code at a different product, when two products turn out to be one and are merged
     * — an extra reconciled to the real product it was. The exception to codes being assigned once:
     * the code did not change, only which single product it names.
     */
    public void reassignTo(Product other) {
        this.product = Objects.requireNonNull(other, "product");
    }

    public String getCode() {
        return code;
    }

    public Origin getOrigin() {
        return origin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
