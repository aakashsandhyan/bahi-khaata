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
package com.bahikhaata.backend.checkout;

import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.MoneyConverter;
import com.bahikhaata.backend.persistence.UuidEntity;
import com.bahikhaata.contracts.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** One line of a completed {@link Sale}, snapshotting the product as it was billed. */
@Entity
@Table(name = "sale_line")
public class SaleLine extends UuidEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sale_id", nullable = false)
    private UUID saleId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "barcode", columnDefinition = "text")
    private String barcode;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "mrp_paise", nullable = false)
    private Money mrp;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "unit_price_paise", nullable = false)
    private Money unitPrice;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "line_total_paise", nullable = false)
    private Money lineTotal;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "saving_paise", nullable = false)
    private Money saving;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    protected SaleLine() {}

    public SaleLine(
            UUID id,
            UUID saleId,
            UUID productId,
            String name,
            String barcode,
            Money mrp,
            Money unitPrice,
            long quantity,
            Money lineTotal,
            Money saving) {
        super(id);
        this.saleId = saleId;
        this.productId = productId;
        this.name = name;
        this.barcode = barcode;
        this.mrp = mrp;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = lineTotal;
        this.saving = saving;
    }

    public UUID getSaleId() {
        return saleId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public String getBarcode() {
        return barcode;
    }

    public Money getMrp() {
        return mrp;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public long getQuantity() {
        return quantity;
    }

    public Money getLineTotal() {
        return lineTotal;
    }

    public Money getSaving() {
        return saving;
    }
}
