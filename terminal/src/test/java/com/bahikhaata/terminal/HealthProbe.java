package com.bahikhaata.terminal;

import com.bahikhaata.contracts.HealthResponse;

/**
 * Diagnostic: asks a running backend for health using the terminal's real client and
 * prints what it got. Run with {@code ./gradlew :terminal:healthProbe}.
 *
 * <p>Exists because the unit tests answer a narrower question than they appear to. They
 * parse JSON this repository wrote by hand; this parses JSON the backend actually
 * produced. Those diverge the moment someone adds a field to a contract type — which has
 * already happened once, in task 1.9.
 *
 * <p>Lives in test sources so it is never packaged into the terminal that ships.
 */
public final class HealthProbe {

    public static void main(String[] args) {
        String uri = args.length > 0 ? args[0] : BackendClient.DEFAULT_BASE_URI;

        try {
            HealthResponse health = new BackendClient(uri).health();
            System.out.println("PROBE OK  status=" + health.status()
                    + " schemaVersion=" + health.schemaVersion());
        } catch (BackendUnavailableException e) {
            System.out.println("PROBE FAILED  " + e.getMessage());
            System.exit(1);
        }
    }
}
