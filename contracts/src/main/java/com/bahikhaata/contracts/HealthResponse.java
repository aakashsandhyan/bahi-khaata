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
