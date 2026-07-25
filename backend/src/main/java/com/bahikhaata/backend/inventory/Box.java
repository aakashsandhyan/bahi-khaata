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
package com.bahikhaata.backend.inventory;

import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.UuidEntity;
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
 * A physical carton, identified by the carrier's tracking number.
 *
 * <p>The unit of work when unpacking: someone scans the number printed on the box and the
 * screen shows what should be inside it. It is also how completeness is judged — without it
 * there is no way to answer which cartons are still unopened.
 *
 * <p>Most boxes are trivial. In the first real consignment, 289 of 533 held exactly one line,
 * so the common case is one scan and done; a few held sixty.
 */
@Entity
@Table(name = "box")
public class Box extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @Column(name = "tracking_number", nullable = false, columnDefinition = "text")
    private String trackingNumber;

    /**
     * When someone said they had finished with this box, or null if they have not.
     *
     * <p>Null covers both "not started" and "part counted". The difference between those two
     * is a question about counts rather than about the box, so it is derived rather than
     * stored — and a part-counted box is a normal state to walk away from at closing time,
     * not an error.
     */
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "finished_at", columnDefinition = "text")
    private Instant finishedAt;

    /**
     * When someone last worked this box — counted a line, added an extra, finished it, or reopened
     * it — or null if nobody has yet. Opening a box to look records nothing; only work counts. It is
     * how the screen offers back the boxes in hand, most recent first.
     */
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "last_activity_at", columnDefinition = "text")
    private Instant lastActivityAt;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    /** For Hibernate. */
    protected Box() {}

    public Box(Lot lot, String trackingNumber) {
        super(newId());
        this.lot = Objects.requireNonNull(lot, "lot");
        this.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber");
    }

    public Lot getLot() {
        return lot;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public boolean isFinished() {
        return finishedAt != null;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    /**
     * Marks the box finished.
     *
     * <p>Deliberately does not check that everything expected was found. A box can be finished
     * short — the goods simply were not in it — and refusing to close it would leave the
     * operator stuck with no way forward. The shortfall stays visible afterwards.
     */
    public void finish(Instant at) {
        this.finishedAt = Objects.requireNonNull(at, "finishing time");
    }

    /** Reopens a box finished by mistake, so counting can continue. */
    public void reopen() {
        this.finishedAt = null;
    }

    /** Marks that the box was just worked, so it surfaces as recently opened. */
    public void recordActivity(Instant at) {
        this.lastActivityAt = Objects.requireNonNull(at, "activity time");
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
