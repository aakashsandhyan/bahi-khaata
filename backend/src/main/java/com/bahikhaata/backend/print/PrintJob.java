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
 * A queued print job for barcode label output on TSC TE-244 thermal printer.
 *
 * <p>Each job is queued independently; the executor polls the queue every 500ms and
 * processes jobs in FIFO order. Status progresses: queued → printing → done (or failed
 * after max retries). Retry logic handles offline printers gracefully.
 */
@Entity
@Table(name = "print_job")
public class PrintJob extends UuidEntity {

    @Column(name = "item_type", nullable = false, columnDefinition = "text")
    private String itemType; // "box", "batch", "product"

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "copies", nullable = false)
    private int copies;

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

    public PrintJob(UUID id, String itemType, UUID itemId, int copies) {
        super(id);
        this.itemType = itemType;
        this.itemId = itemId;
        this.copies = copies;
        this.status = "queued";
        this.retryCount = 0;
    }

    public static PrintJob create(String itemType, UUID itemId, int copies) {
        return new PrintJob(newId(), itemType, itemId, copies);
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public UUID getItemId() {
        return itemId;
    }

    public void setItemId(UUID itemId) {
        this.itemId = itemId;
    }

    public int getCopies() {
        return copies;
    }

    public void setCopies(int copies) {
        this.copies = copies;
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
