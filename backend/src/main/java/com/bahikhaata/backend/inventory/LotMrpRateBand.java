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
package com.bahikhaata.backend.inventory;

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

/**
 * One band of a {@code MRP_RATE_RANGE} lot's rate card: an item whose MRP falls in
 * {@code [minMrp, maxMrp)} costs {@code cost} — {@code maxMrp} null is the open-topped final
 * band. A child row rather than a JSON blob on the lot, so bands are queryable and editable one
 * at a time like every other row in this schema.
 *
 * <p>A lot's whole rate card is replaced together on edit ({@code deleteByLotId} then re-insert),
 * so nothing outside {@link LotController} needs to know one row from another by identity.
 */
@Entity
@Table(name = "lot_mrp_rate_band")
public class LotMrpRateBand extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "min_mrp_paise", nullable = false)
    private Money minMrp;

    /** Exclusive; null means this is the open-topped final band. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "max_mrp_paise")
    private Money maxMrp;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "cost_paise", nullable = false)
    private Money cost;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    /** For Hibernate. */
    protected LotMrpRateBand() {}

    public LotMrpRateBand(Lot lot, Money minMrp, Money maxMrp, Money cost) {
        super(newId());
        this.lot = Objects.requireNonNull(lot, "lot");
        this.minMrp = Objects.requireNonNull(minMrp, "minMrp");
        this.maxMrp = maxMrp;
        this.cost = Objects.requireNonNull(cost, "cost");
    }

    public Lot getLot() {
        return lot;
    }

    public Money getMinMrp() {
        return minMrp;
    }

    /** Exclusive upper bound, or null for the open-topped final band. */
    public Money getMaxMrp() {
        return maxMrp;
    }

    public Money getCost() {
        return cost;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
