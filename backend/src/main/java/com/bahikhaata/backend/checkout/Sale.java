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

import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.MoneyConverter;
import com.bahikhaata.backend.persistence.UuidEntity;
import com.bahikhaata.contracts.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A completed sale — the record a bill is rendered from.
 *
 * <p>Immutable once written: it snapshots the totals and (through {@link SaleLine}) each line, so a
 * later reprice or rename never alters a past bill. {@code tax} is zero for now — the shop bills as
 * a composition Bill of Supply, collecting no tax — but is carried so a figure can be recorded
 * later without a schema change.
 */
@Entity
@Table(name = "sale")
public class Sale extends UuidEntity {

    @Column(name = "bill_no", nullable = false)
    private long billNo;

    @Column(name = "payment_method", nullable = false, columnDefinition = "text")
    private String paymentMethod; // CASH, UPI, CARD

    @Convert(converter = MoneyConverter.class)
    @Column(name = "subtotal_paise", nullable = false)
    private Money subtotal;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "saving_paise", nullable = false)
    private Money saving;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "tax_paise", nullable = false)
    private Money tax;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "cgst_paise", nullable = false)
    private Money cgst;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "sgst_paise", nullable = false)
    private Money sgst;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "taxable_paise", nullable = false)
    private Money taxable;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "total_paise", nullable = false)
    private Money total;

    @Column(name = "operator_name", columnDefinition = "text")
    private String operatorName;

    /** The drawer session this sale was rung under, or null — the classic till and all history
     * before registers sell sessionless, and that stays valid forever. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "register_session_id")
    private java.util.UUID registerSessionId;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    protected Sale() {}

    public Sale(
            long billNo,
            String paymentMethod,
            Money subtotal,
            Money saving,
            Money tax,
            Money cgst,
            Money sgst,
            Money taxable,
            Money total,
            String operatorName) {
        super(newId());
        this.billNo = billNo;
        this.paymentMethod = paymentMethod;
        this.subtotal = subtotal;
        this.saving = saving;
        this.tax = tax;
        this.cgst = cgst;
        this.sgst = sgst;
        this.taxable = taxable;
        this.total = total;
        this.operatorName = operatorName;
    }

    public long getBillNo() {
        return billNo;
    }

    /** The bill number as it prints — a stable, human-friendly form. */
    public String formattedBillNo() {
        return String.format("BB-%06d", billNo);
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public Money getSubtotal() {
        return subtotal;
    }

    public Money getSaving() {
        return saving;
    }

    public Money getTax() {
        return tax;
    }

    public Money getCgst() {
        return cgst;
    }

    public Money getSgst() {
        return sgst;
    }

    public Money getTaxable() {
        return taxable;
    }

    public Money getTotal() {
        return total;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public java.util.UUID getRegisterSessionId() {
        return registerSessionId;
    }

    public void setRegisterSessionId(java.util.UUID registerSessionId) {
        this.registerSessionId = registerSessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
