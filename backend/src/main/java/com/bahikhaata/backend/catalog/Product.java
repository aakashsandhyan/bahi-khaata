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
package com.bahikhaata.backend.catalog;

import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.MoneyConverter;
import com.bahikhaata.backend.persistence.UuidEntity;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A product in the catalogue.
 *
 * <p>Mutable — name, price, and attributes change over a product's life — so unlike ledger
 * and invoice rows it carries no immutability trigger and has an {@code updated_at}.
 *
 * <p>Selling price is nullable and is the product's alone: an unpriced product has a null
 * price (never zero), and receiving stock at any cost never changes it. Those rules are
 * enforced by the operations added in later tasks; this class holds the state they act on.
 *
 * <p>Column types are pinned to match the reviewed V3 migration under {@code ddl-auto=validate}:
 * text columns are declared {@code text} rather than the default {@code varchar(255)}, so a
 * product name is not silently capped at 255 characters and the migration stays idiomatic
 * SQLite. Instant/boolean/JSON types were confirmed against the dialect in task 2.1.
 */
@Entity
@Table(name = "product")
public class Product extends UuidEntity {

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, columnDefinition = "text")
    private Category category;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "selling_price_paise")
    private Money sellingPrice;

    @Column(name = "hsn_code", columnDefinition = "text")
    private String hsnCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes")
    private Map<String, Object> attributes;

    @Column(name = "price_review_flagged", nullable = false)
    private boolean priceReviewFlagged;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    /** For Hibernate. */
    protected Product() {}

    /**
     * Creates an unpriced product. Price is set later, deliberately: stock is routinely
     * received before anyone has decided what it is worth.
     */
    public Product(String name, Category category, Map<String, Object> attributes) {
        super(newId());
        this.name = Objects.requireNonNull(name, "name");
        this.category = Objects.requireNonNull(category, "category");
        this.attributes = attributes;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    /** The selling price, or null when the product is unpriced. Never zero for "no price". */
    public Money getSellingPrice() {
        return sellingPrice;
    }

    /**
     * Whether a selling price has been set. The explicit question to ask before treating a
     * product as sellable — an unpriced product is a real, legitimate state (stock received
     * before it has been valued), not an error and not a price of zero.
     */
    public boolean isPriced() {
        return sellingPrice != null;
    }

    /**
     * Sets the selling price. This is the only thing that changes a product's price: no other
     * operation — not receiving stock at a new cost, not flagging for margin review — may
     * touch it. That is the invariant the whole no-auto-repricing model rests on.
     *
     * <p>The price must be a real, positive amount. A product cannot be un-priced by passing
     * null, and cannot be set to zero or a negative value.
     */
    public void setSellingPrice(Money price) {
        Objects.requireNonNull(price, "selling price (a product cannot be un-priced)");
        if (!price.isPositive()) {
            throw new IllegalArgumentException("selling price must be positive, was " + price);
        }
        this.sellingPrice = price;
    }

    public String getHsnCode() {
        return hsnCode;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public boolean isPriceReviewFlagged() {
        return priceReviewFlagged;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
