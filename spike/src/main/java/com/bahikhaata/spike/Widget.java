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
