package com.bahikhaata.spike;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Proves that {@code @Immutable} stops Hibernate's dirty checking from ever emitting an UPDATE.
 * The field is deliberately mutable in Java so the test can change it and confirm Hibernate
 * ignores the change — that is the whole point of the check.
 */
@Entity
@Immutable
public class LedgerRow {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "movement_type", nullable = false)
    private String movementType;

    protected LedgerRow() {
        // for Hibernate
    }

    public LedgerRow(long quantity, String movementType) {
        this.id = UUID.randomUUID().toString();
        this.quantity = quantity;
        this.movementType = movementType;
    }

    public String getId() {
        return id;
    }

    public long getQuantity() {
        return quantity;
    }

    public String getMovementType() {
        return movementType;
    }

    /** Only exists so the test can attempt a mutation Hibernate must refuse to persist. */
    void attemptMutation(long newQuantity) {
        this.quantity = newQuantity;
    }
}
