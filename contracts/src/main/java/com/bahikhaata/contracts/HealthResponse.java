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
package com.bahikhaata.contracts;

/**
 * Whether the backend is ready to serve, and at which schema version.
 *
 * <p>The terminal holds no database of its own, so it cannot present a checkout screen
 * until this answers. "Ready" here means the database is reachable and migrated — not
 * merely that the process is running. An endpoint that reports healthy while the database
 * is unreachable is worse than none: the cashier gets a checkout screen and discovers the
 * problem on the first scan, with a customer waiting.
 *
 * @param status whether the backend can serve requests
 * @param schemaVersion the highest successfully applied migration version, or null when
 *     the backend is not ready
 */
public record HealthResponse(Status status, String schemaVersion) {

    public enum Status {
        /** Database reachable and migrated. Safe to proceed. */
        UP,
        /** Process running but the database is not usable. Do not proceed. */
        DOWN
    }

    public static HealthResponse up(String schemaVersion) {
        return new HealthResponse(Status.UP, schemaVersion);
    }

    public static HealthResponse down() {
        return new HealthResponse(Status.DOWN, null);
    }

    // Deliberately no isUp() convenience method. Jackson treats a bean-style getter on a
    // record as an extra property, so it would appear on the wire as a derived "up" field
    // that could contradict "status". Callers compare status directly.
}
