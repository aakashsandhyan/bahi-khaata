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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Singleton printer configuration.
 *
 * <p>Admin configures printer address (IP:port for network, /dev/ttyUSB0 for USB),
 * port speed, default copies, and paper size. A test endpoint checks connectivity
 * and stores the result (OK, UNREACHABLE, ERROR). Only one row exists per installation.
 */
@Entity
@Table(name = "printer_config")
public class PrinterConfig extends UuidEntity {

    public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Column(name = "address", nullable = false, length = 255)
    private String address; // "192.168.1.100:9100" or "/dev/ttyUSB0"

    @Column(name = "port_speed", nullable = false)
    private int portSpeed; // e.g., 9600

    @Column(name = "paper_size", nullable = false, length = 10)
    private String paperSize; // "4x6"

    @Column(name = "copies_default", nullable = false)
    private int copiesDefault; // 1–5

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "test_status", columnDefinition = "text")
    private String testStatus; // "OK", "UNREACHABLE", "ERROR"

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

    protected PrinterConfig() {}

    public PrinterConfig(
        UUID id, String address, int portSpeed, String paperSize, int copiesDefault, boolean enabled) {
        super(id);
        this.address = address;
        this.portSpeed = portSpeed;
        this.paperSize = paperSize;
        this.copiesDefault = copiesDefault;
        this.enabled = enabled;
    }

    public static PrinterConfig createDefault() {
        return new PrinterConfig(
            SINGLETON_ID,
            "192.168.1.100:9100",
            9600,
            "4x6",
            1,
            true);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getPortSpeed() {
        return portSpeed;
    }

    public void setPortSpeed(int portSpeed) {
        this.portSpeed = portSpeed;
    }

    public String getPaperSize() {
        return paperSize;
    }

    public void setPaperSize(String paperSize) {
        this.paperSize = paperSize;
    }

    public int getCopiesDefault() {
        return copiesDefault;
    }

    public void setCopiesDefault(int copiesDefault) {
        this.copiesDefault = copiesDefault;
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

    public void setTestStatus(String testStatus) {
        this.testStatus = testStatus;
    }

    public String getTestError() {
        return testError;
    }

    public void setTestError(String testError) {
        this.testError = testError;
    }

    public Instant getLastTestedAt() {
        return lastTestedAt;
    }

    public void setLastTestedAt(Instant lastTestedAt) {
        this.lastTestedAt = lastTestedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
