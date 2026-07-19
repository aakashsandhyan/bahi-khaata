/*
 * bahi-khaata — point of sale for Bachat Baazar
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
