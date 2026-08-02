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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Singleton configuration for the ESC/POS receipt printer — a second physical printer, separate
 * from the label printer's {@link PrinterConfig}. The address is a {@code host:port} (raw-9100) for
 * a network printer or the OS printer name for a USB one, per {@code transport}.
 */
@Entity
@Table(name = "receipt_printer_config")
public class ReceiptPrinterConfig extends UuidEntity {

    public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "transport", nullable = false, length = 20)
    private String transport; // LAN, USB

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "test_status", columnDefinition = "text")
    private String testStatus; // OK, UNREACHABLE, ERROR

    @Column(name = "test_error", columnDefinition = "text")
    private String testError;

    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "last_tested_at", columnDefinition = "text")
    private Instant lastTestedAt;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    protected ReceiptPrinterConfig() {}

    public ReceiptPrinterConfig(UUID id, String address, String transport, boolean enabled) {
        super(id);
        this.address = address;
        this.transport = transport;
        this.enabled = enabled;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTestStatus() {
        return testStatus;
    }

    public String getTestError() {
        return testError;
    }

    public Instant getLastTestedAt() {
        return lastTestedAt;
    }

    /** Records the outcome of a test print. */
    public void recordTest(String status, String error, Instant at) {
        this.testStatus = status;
        this.testError = error;
        this.lastTestedAt = at;
    }
}
