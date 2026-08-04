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
package com.bahikhaata.backend.checkout;

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
 * One product on a cart, at the price and MRP it carried when it was added.
 *
 * <p>Both figures are snapshotted, not read live: a reprice on the dashboard mid-sale must not
 * change what the customer was quoted. The saving the customer is shown is this MRP against this
 * price, held steady through the transaction.
 */
@Entity
@Table(name = "cart_line")
public class CartLine extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "unit_price_paise", nullable = false)
    private Money unitPrice;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "mrp_paise", nullable = false)
    private Money mrp;

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

    protected CartLine() {}

    public CartLine(Cart cart, Product product, Money unitPrice, Money mrp, long quantity) {
        super(newId());
        this.cart = Objects.requireNonNull(cart);
        this.product = Objects.requireNonNull(product);
        this.unitPrice = Objects.requireNonNull(unitPrice);
        this.mrp = Objects.requireNonNull(mrp);
        this.quantity = quantity;
    }

    public Cart getCart() {
        return cart;
    }

    public Product getProduct() {
        return product;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public Money getMrp() {
        return mrp;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive; remove the line instead");
        }
        this.quantity = quantity;
    }

    public void addOne() {
        this.quantity += 1;
    }

    /** The line's whole price: unit price times how many. */
    public Money lineTotal() {
        return unitPrice.times(quantity);
    }

    /** What the customer saves on this line against the printed MRP. */
    public Money saving() {
        return mrp.minus(unitPrice).times(quantity);
    }
}
