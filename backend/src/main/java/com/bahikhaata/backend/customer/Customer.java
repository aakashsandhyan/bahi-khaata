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
package com.bahikhaata.backend.customer;

import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A customer the counter knows: a name and the normalized ten-digit mobile that identifies them.
 * One customer per number — the mobile is the key the counter asks for first. Everything else
 * about a customer (visits, spend, likes) is derived from their sales, never stored here.
 */
@Entity
@Table(name = "customer")
public class Customer extends UuidEntity {

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    /** Exactly ten digits, normalized — see {@link CustomerService#normalizeMobile}. */
    @Column(name = "mobile", nullable = false, unique = true, columnDefinition = "text")
    private String mobile;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    protected Customer() {}

    public Customer(String name, String mobile) {
        super(newId());
        this.name = name;
        this.mobile = mobile;
    }

    public String getName() {
        return name;
    }

    /** The counter's latest utterance is the freshest record — see the upsert rule. */
    public void refreshName(String name) {
        this.name = name;
    }

    public String getMobile() {
        return mobile;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
