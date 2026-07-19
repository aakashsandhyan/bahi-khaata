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
package com.bahikhaata.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Base for every persistent entity: a UUIDv4 identifier stored as readable
 * 36-character text.
 *
 * <p>UUIDs rather than auto-increment integers from day one, even though multi-terminal
 * sync is not being built. Two terminals independently generating "order 50" collide the
 * moment those records need to meet, and retrofitting identifiers after real invoices
 * exist is expensive in a way that choosing them now is not.
 *
 * <p>Text rather than a 16-byte blob because the constraint on this project is review and
 * diagnosis effort, not disk. At a few thousand SKUs the storage difference is
 * unmeasurable, while a legible identifier makes every log line and manual {@code sqlite3}
 * query easier. It also maps cleanly onto Postgres's native uuid type later.
 *
 * <p><strong>The matching column must be declared {@code CHAR(36)}.</strong> Not
 * {@code TEXT} — Hibernate's schema validator compares declared types and aborts startup
 * with "found [text (Types#VARCHAR)], but expecting [char(36) (Types#CHAR)]". SQLite gives
 * both TEXT affinity, so this is a declaration rule, not a storage difference.
 *
 * <p>Identifiers are assigned at construction rather than by the database. Entities are
 * therefore complete before they are persisted, which is what lets ledger and invoice rows
 * be genuinely immutable — an identifier filled in on flush would mean a mutation.
 */
@MappedSuperclass
public abstract class UuidEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    /** For Hibernate only. It populates the field directly when hydrating a row. */
    protected UuidEntity() {}

    protected UuidEntity(UUID id) {
        this.id = Objects.requireNonNull(id, "entity id");
    }

    /** Convenience for subclass constructors: {@code super(UuidEntity.newId())}. */
    protected static UUID newId() {
        return UUID.randomUUID();
    }

    public UUID getId() {
        return id;
    }

    /**
     * Identity is the identifier and the entity type. Safe because identifiers are assigned
     * at construction and never change, so an entity has stable identity before it is
     * persisted — unlike database-generated ids, which change what equality means mid-flight.
     *
     * <p>Compares {@link Hibernate#getClass} rather than {@code getClass()} so a lazy proxy
     * still equals the entity it stands for.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!Hibernate.getClass(this).equals(Hibernate.getClass(other))) {
            return false;
        }
        UuidEntity that = (UuidEntity) other;
        return this.id != null && this.id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    @Override
    public String toString() {
        return Hibernate.getClass(this).getSimpleName() + "[" + id + "]";
    }
}
