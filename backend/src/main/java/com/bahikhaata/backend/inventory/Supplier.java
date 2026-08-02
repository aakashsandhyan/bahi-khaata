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
import com.bahikhaata.backend.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One vendor goods are bought from.
 *
 * <p>Suppliers were once a free-typed string repeated on every lot, so the same vendor
 * fragmented across casing and spacing. This entity is the one canonical record per vendor,
 * and every lot now points at it.
 *
 * <p>Identity is two-fold. A GSTIN, when the vendor has one, is the legal identity and is
 * unique. Many small vendors are unregistered and have none, so the normalised name — trimmed,
 * internal runs of whitespace collapsed, lower-cased — is the fallback identity and is unique
 * too. The stored {@code name} keeps its original casing for display; {@code nameNormalized} is
 * the key both the uniqueness index and the lookups compare on, so the rule is identical in the
 * database and in code.
 *
 * <p>Never hard-deleted, because lots reference it: retiring a vendor sets {@code active} false,
 * which hides it from the pick-list at receipt while leaving its history intact.
 */
@Entity
@Table(name = "supplier")
public class Supplier extends UuidEntity {

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    /**
     * The normalised form of {@link #name}: trimmed, internal whitespace collapsed to single
     * spaces, lower-cased. Carries the uniqueness index and is what lookups match on. Kept in its
     * own column rather than an expression index so the normalisation lives in reviewable Java and
     * reads identically to the way rows are found.
     */
    @Column(name = "name_normalized", nullable = false, columnDefinition = "text")
    private String nameNormalized;

    /** The 15-character GSTIN, or null for an unregistered vendor. Unique when present. */
    @Column(name = "gstin", columnDefinition = "text")
    private String gstin;

    @Column(name = "phone", columnDefinition = "text")
    private String phone;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "contact_person", columnDefinition = "text")
    private String contactPerson;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    /** For Hibernate. */
    protected Supplier() {}

    public Supplier(String name, String gstin, String phone, String address,
            String contactPerson, String notes) {
        super(newId());
        setName(name);
        this.gstin = blankToNull(gstin);
        this.phone = blankToNull(phone);
        this.address = blankToNull(address);
        this.contactPerson = blankToNull(contactPerson);
        this.notes = blankToNull(notes);
        this.active = true;
    }

    /**
     * The key both the uniqueness index and lookups compare on. The single source of the
     * normalisation rule — the migration mirrors its trim-and-lower in SQL.
     */
    public static String normalize(String name) {
        Objects.requireNonNull(name, "name");
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("supplier name required");
        }
        this.name = name.trim();
        this.nameNormalized = normalize(name);
    }

    public void setGstin(String gstin) {
        this.gstin = blankToNull(gstin);
    }

    public void setPhone(String phone) {
        this.phone = blankToNull(phone);
    }

    public void setAddress(String address) {
        this.address = blankToNull(address);
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = blankToNull(contactPerson);
    }

    public void setNotes(String notes) {
        this.notes = blankToNull(notes);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getName() {
        return name;
    }

    public String getNameNormalized() {
        return nameNormalized;
    }

    public String getGstin() {
        return gstin;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
