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
package com.bahikhaata.backend.print;

import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.UuidEntity;
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

/**
 * A queued, self-contained label print job for the TSC TE-244.
 *
 * <p>The job carries the label fields it needs — barcode, product name, selling price, optional
 * MRP — so the executor renders it without reading the database. A later price change therefore
 * cannot alter a queued label, and a reprint just queues fresh values.
 *
 * <p>{@code productId} is a back-reference only: on a successful print the executor marks that
 * product's label as printed, and the bulk screen uses it to tell what is done. It is never read
 * to render.
 *
 * <p>The executor polls the queue every 500ms and processes jobs in FIFO order. Status
 * progresses: queued → printing → done (or failed after max retries).
 */
@Entity
@Table(name = "print_job")
public class PrintJob extends UuidEntity {

    @Column(name = "barcode", nullable = false, columnDefinition = "text")
    private String barcode;

    @Column(name = "product_name", nullable = false, columnDefinition = "text")
    private String productName;

    @Column(name = "selling_price_paise", nullable = false)
    private long sellingPricePaise;

    @Column(name = "mrp_paise")
    private Long mrpPaise;

    @Column(name = "copies", nullable = false)
    private int copies;

    /** Back-reference for the printed-marker and bulk view only — never read to render. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status; // "queued", "printing", "done", "failed"

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    protected PrintJob() {}

    public PrintJob(
            UUID id,
            String barcode,
            String productName,
            long sellingPricePaise,
            Long mrpPaise,
            int copies,
            UUID productId) {
        super(id);
        this.barcode = barcode;
        this.productName = productName;
        this.sellingPricePaise = sellingPricePaise;
        this.mrpPaise = mrpPaise;
        this.copies = copies;
        this.productId = productId;
        this.status = "queued";
        this.retryCount = 0;
    }

    public static PrintJob create(
            String barcode,
            String productName,
            long sellingPricePaise,
            Long mrpPaise,
            int copies,
            UUID productId) {
        return new PrintJob(newId(), barcode, productName, sellingPricePaise, mrpPaise, copies, productId);
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public long getSellingPricePaise() {
        return sellingPricePaise;
    }

    public void setSellingPricePaise(long sellingPricePaise) {
        this.sellingPricePaise = sellingPricePaise;
    }

    public Long getMrpPaise() {
        return mrpPaise;
    }

    public void setMrpPaise(Long mrpPaise) {
        this.mrpPaise = mrpPaise;
    }

    public int getCopies() {
        return copies;
    }

    public void setCopies(int copies) {
        this.copies = copies;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
