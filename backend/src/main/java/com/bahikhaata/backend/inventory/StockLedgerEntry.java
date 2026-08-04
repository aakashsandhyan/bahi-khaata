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

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.MoneyConverter;
import com.bahikhaata.backend.persistence.UuidEntity;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.MovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

/**
 * One movement of stock: the ledger is append-only, and this row never changes.
 *
 * <p>Immutability is enforced twice, and the two layers do different jobs. {@code @Immutable}
 * stops Hibernate's dirty checking from ever generating an {@code UPDATE} — the specific
 * hazard JPA introduces — and gives a clear failure during development. The database triggers
 * stop everything else: a migration, a maintenance query, a future change that forgets. The
 * application guard is the one you notice; the database guard is the one that still holds
 * when the application layer is wrong.
 *
 * <p>There are no setters and no {@code updated_at}. Nothing here may change, so there is
 * nothing to track. A correction is a new, opposing movement — never an edit.
 */
@Entity
@Immutable
@Table(name = "stock_ledger")
public class StockLedgerEntry extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    /** Signed: positive is stock arriving, negative is stock leaving. Never zero. */
    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, columnDefinition = "text")
    private MovementType movementType;

    /** Cost of goods sold at the consumed batch's cost. Null on inward movements. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "cogs_paise")
    private Money cogs;

    /**
     * When the movement happened in business terms, not when the row was written. A delivery
     * logged two days late is appended at its true effective time, and derived figures
     * recalculate forward from there.
     */
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "effective_at", nullable = false, columnDefinition = "text")
    private Instant effectiveAt;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    /** For Hibernate. */
    protected StockLedgerEntry() {}

    /**
     * Stock arriving from a supplier. Takes a positive count; an arrival has no cost of goods
     * sold.
     */
    public static StockLedgerEntry receipt(
            Product product, Batch batch, long quantity, Instant effectiveAt) {
        requireCount(quantity, "a receipt must bring stock in");
        return new StockLedgerEntry(
                product, batch, quantity, MovementType.PURCHASE_RECEIPT, null, effectiveAt);
    }

    /**
     * The receipt for a whole batch: brings in its <em>sellable</em> quantity, not everything
     * that arrived.
     *
     * <p>Units damaged on arrival never became sellable stock, so they never enter the ledger.
     * They are not lost information — the batch records how many arrived and how many were
     * damaged, and their cost is absorbed by the units that can be sold. {@link
     * MovementType#WRITE_OFF} is reserved for damage found <em>later</em>, once stock was
     * genuinely on hand and has to come back off it.
     */
    public static StockLedgerEntry receiptOf(Batch batch, Instant effectiveAt) {
        Objects.requireNonNull(batch, "batch");
        return receipt(batch.getProduct(), batch, batch.sellableQuantity(), effectiveAt);
    }

    /**
     * Stock leaving through a sale. Takes a positive count and records it negative, so a
     * caller cannot accidentally book a sale that adds stock. Cost of goods sold is required:
     * a sale whose cost is unknown would silently break margin reporting.
     */
    public static StockLedgerEntry sale(
            Product product, Batch batch, long quantity, Money cogs, Instant effectiveAt) {
        requireCount(quantity, "a sale must take stock out");
        Objects.requireNonNull(cogs, "cost of goods sold is required on a sale");
        return new StockLedgerEntry(
                product, batch, -quantity, MovementType.SALE, cogs, effectiveAt);
    }

    /**
     * Stock leaving because it cannot be sold. Takes a positive count and records it negative.
     * Carries no cost of goods sold — nothing was sold, so attributing COGS would overstate
     * the cost of what actually earned.
     */
    public static StockLedgerEntry writeOff(
            Product product, Batch batch, long quantity, Instant effectiveAt) {
        requireCount(quantity, "a write-off must take stock out");
        return new StockLedgerEntry(
                product, batch, -quantity, MovementType.WRITE_OFF, null, effectiveAt);
    }

    /**
     * A correction after a stock take. The only movement that takes an already-signed
     * quantity, because a count can legitimately come out either high or low.
     */
    public static StockLedgerEntry adjustment(
            Product product, Batch batch, long signedQuantity, Instant effectiveAt) {
        return new StockLedgerEntry(
                product, batch, signedQuantity, MovementType.ADJUSTMENT, null, effectiveAt);
    }

    private static void requireCount(long quantity, String message) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(message + ", so its count must be positive");
        }
    }

    private StockLedgerEntry(
            Product product,
            Batch batch,
            long quantity,
            MovementType movementType,
            Money cogs,
            Instant effectiveAt) {
        super(newId());
        this.product = Objects.requireNonNull(product, "product");
        this.batch = Objects.requireNonNull(batch, "batch");
        this.movementType = Objects.requireNonNull(movementType, "movementType");
        this.effectiveAt = Objects.requireNonNull(effectiveAt, "effectiveAt");

        if (quantity == 0) {
            throw new IllegalArgumentException("a movement of zero is not a movement");
        }
        if (cogs != null && quantity > 0) {
            throw new IllegalArgumentException(
                    "cost of goods sold belongs only to stock leaving, not to an arrival");
        }
        this.quantity = quantity;
        this.cogs = cogs;
    }

    public Product getProduct() {
        return product;
    }

    public Batch getBatch() {
        return batch;
    }

    public long getQuantity() {
        return quantity;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    /** Cost of goods sold, or null for an inward movement. */
    public Money getCogs() {
        return cogs;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
