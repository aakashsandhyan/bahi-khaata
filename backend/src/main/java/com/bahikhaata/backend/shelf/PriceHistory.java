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
package com.bahikhaata.backend.shelf;

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
import org.hibernate.annotations.Immutable;

/**
 * One recorded change to a product's selling price: append-only, and the durable record of every
 * price this product has ever carried.
 *
 * <p>Written from exactly one place — {@link ProductPricing#setSellingPrice}, the single choke
 * point every price-setting path funnels through (design decision D5 of palletworks-inventory).
 * No other class constructs or saves one; a caller that wants a price changed calls the choke
 * point, and the journal follows automatically.
 *
 * <p>{@code oldPrice} is null on a product's first-ever price set — there is nothing to have
 * been before it, and recording a fabricated zero would read as a real transition. {@code
 * operatorName} is null for now: the choke point's signature carries no operator (D6), and this
 * column exists so a later change can backfill it without a rewrite.
 *
 * <p>Immutability is enforced the same two ways {@link
 * com.bahikhaata.backend.inventory.StockLedgerEntry} is: {@code @Immutable} stops Hibernate's
 * dirty checking from ever emitting an {@code UPDATE}, and the database triggers from V45 (mirror
 * of V6's stock-ledger pair) refuse an {@code UPDATE} or {@code DELETE} however it is attempted.
 * There is no {@code updated_at}: nothing here may change, so there is nothing to track.
 */
@Entity
@Immutable
@Table(name = "price_history")
public class PriceHistory extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "old_price_paise")
    private Money oldPrice;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "new_price_paise", nullable = false)
    private Money newPrice;

    /** Who made the change, or null — not captured at the choke point yet (D6). */
    @Column(name = "operator_name", columnDefinition = "text")
    private String operatorName;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    /** For Hibernate. */
    protected PriceHistory() {}

    /**
     * Records one price change. {@code oldPrice} is null for a first-ever set; {@code newPrice}
     * is required — a change always changes to something.
     */
    public static PriceHistory record(Product product, Money oldPrice, Money newPrice) {
        return new PriceHistory(product, oldPrice, newPrice);
    }

    private PriceHistory(Product product, Money oldPrice, Money newPrice) {
        super(newId());
        this.product = Objects.requireNonNull(product, "product");
        this.newPrice = Objects.requireNonNull(newPrice, "newPrice");
        this.oldPrice = oldPrice;
    }

    public Product getProduct() {
        return product;
    }

    /** The price this product carried immediately before this change, or null on a first set. */
    public Money getOldPrice() {
        return oldPrice;
    }

    public Money getNewPrice() {
        return newPrice;
    }

    /** Who made the change, or null — not captured yet (D6). */
    public String getOperatorName() {
        return operatorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
