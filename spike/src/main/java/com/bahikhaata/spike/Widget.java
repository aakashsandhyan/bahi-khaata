/*
 * bahi-khaata — point of sale for Bachat Bazar
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
package com.bahikhaata.spike;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Proves two things at once: a JSON column mapped through {@code @JdbcTypeCode(SqlTypes.JSON)},
 * and an integer column holding money as paise.
 */
@Entity
public class Widget {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes")
    private Map<String, Object> attributes;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    protected Widget() {
        // for Hibernate
    }

    public Widget(String name, Map<String, Object> attributes, long pricePaise) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.attributes = attributes;
        this.pricePaise = pricePaise;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public long getPricePaise() {
        return pricePaise;
    }
}
