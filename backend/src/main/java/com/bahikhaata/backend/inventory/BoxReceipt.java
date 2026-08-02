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
import com.bahikhaata.contracts.BoxState;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Receipt record for a single carton/box in a lot. */
@Entity
@Table(name = "box_receipt")
public class BoxReceipt extends UuidEntity {

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "lot_id", nullable = false)
  private java.util.UUID lotId;

  @Column(name = "manifest_carton_id", nullable = false, columnDefinition = "text")
  private String manifestCartonId;

  @Enumerated(EnumType.STRING)
  @Column(name = "box_state", nullable = false, columnDefinition = "text")
  private BoxState state = BoxState.EXPECTED;

  @Convert(converter = InstantIso8601Converter.class)
  @Column(name = "received_at", columnDefinition = "text")
  private Instant receivedAt;

  @Column(name = "rejected_reason", columnDefinition = "text")
  private String rejectedReason;

  @CreationTimestamp
  @Convert(converter = InstantIso8601Converter.class)
  @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
  private Instant createdAt;

  @UpdateTimestamp
  @Convert(converter = InstantIso8601Converter.class)
  @Column(name = "updated_at", nullable = false, columnDefinition = "text")
  private Instant updatedAt;

  protected BoxReceipt() {}

  public BoxReceipt(java.util.UUID lotId, String manifestCartonId) {
    super(newId());
    this.lotId = Objects.requireNonNull(lotId, "lotId");
    this.manifestCartonId = Objects.requireNonNull(manifestCartonId, "manifestCartonId");
  }

  public void markReceived(Instant at) {
    if (state != BoxState.EXPECTED) {
      throw new IllegalStateException(
          "box " + manifestCartonId + " is in state " + state + ", cannot mark received");
    }
    this.state = BoxState.RECEIVED;
    this.receivedAt = Objects.requireNonNull(at, "received timestamp");
  }

  public void markUnpacking() {
    if (state != BoxState.RECEIVED) {
      throw new IllegalStateException(
          "box " + manifestCartonId + " must be RECEIVED before unpacking, but is " + state);
    }
    this.state = BoxState.UNPACKING;
  }

  public void markUnpacked() {
    if (state != BoxState.UNPACKING) {
      throw new IllegalStateException(
          "box " + manifestCartonId + " must be in UNPACKING to mark unpacked, but is " + state);
    }
    this.state = BoxState.UNPACKED;
  }

  public void reject(String reason) {
    if (!(state == BoxState.EXPECTED || state == BoxState.RECEIVED
        || state == BoxState.UNPACKING)) {
      throw new IllegalStateException(
          "box " + manifestCartonId + " cannot be rejected from state " + state);
    }
    this.state = BoxState.REJECTED;
    this.rejectedReason = Objects.requireNonNull(reason, "rejection reason");
  }

  public void markNotReceived() {
    if (state != BoxState.EXPECTED) {
      throw new IllegalStateException(
          "box " + manifestCartonId + " must be EXPECTED to mark not received, but is " + state);
    }
    this.state = BoxState.NOT_RECEIVED;
  }

  public java.util.UUID getLotId() {
    return lotId;
  }

  public String getManifestCartonId() {
    return manifestCartonId;
  }

  public BoxState getState() {
    return state;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public String getRejectedReason() {
    return rejectedReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
