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

import com.bahikhaata.contracts.CartonProgress;
import com.bahikhaata.contracts.CountOutcome;
import com.bahikhaata.contracts.UnpackingCarton;
import com.bahikhaata.contracts.UnpackingLine;
import java.util.List;
import java.util.UUID;

/**
 * Diagnostic: walks one carton through the terminal's real client against a running backend.
 *
 * <p>Run with {@code ./gradlew :terminal:unpackingProbe --args="<trackingNumber>"}.
 *
 * <p>Exists for the same reason {@link HealthProbe} does, and the reason has already paid twice.
 * Unit tests here parse JSON this repository wrote by hand; this parses JSON the backend
 * actually produced. The two diverge the moment a contract gains a field — and separately, a
 * controller reading a lazily-loaded name passed every in-process test and threw against a real
 * backend, because those tests ran inside a transaction and a real request does not.
 *
 * <p>Reads and counts against real data, so point it at a development database.
 *
 * <p>Lives in test sources so it is never packaged into the terminal that ships.
 */
public final class UnpackingProbe {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("usage: unpackingProbe <trackingNumber> [baseUri]");
            System.exit(2);
        }
        String tracking = args[0];
        BackendClient backend =
                new BackendClient(args.length > 1 ? args[1] : BackendClient.DEFAULT_BASE_URI);

        try {
            List<UnpackingCarton> found = backend.cartonsByTracking(tracking);
            if (found.isEmpty()) {
                System.out.println("PROBE FAILED  no carton bears the number " + tracking);
                System.exit(1);
            }
            UnpackingCarton carton = found.get(0);
            System.out.println("  scan box       " + carton.trackingNumber()
                    + "  finished=" + carton.finished());

            List<UnpackingLine> lines = backend.linesIn(carton.boxId());
            System.out.println("  contents       " + lines.size() + " line(s)");
            for (UnpackingLine line : lines) {
                System.out.println("    " + line.counted() + "/" + line.expected()
                        + "  needsMrp=" + line.needsMrp() + "  " + trim(line.name()));
            }

            UnpackingLine first = lines.get(0);
            if (first.outstanding() > 0) {
                CountOutcome counted =
                        backend.count(first.lineId(), 1, first.needsMrp() ? 24_900L : null, false);
                System.out.println("  count one      " + counted.quantityCounted() + " of "
                        + counted.quantityExpected());

                UnpackingLine after = backend.linesIn(carton.boxId()).get(0);
                System.out.println("  asked again?   needsMrp=" + after.needsMrp()
                        + "   (false once the price is known)");
            }

            List<CartonProgress> delivery = backend.cartonsInDelivery(carton.lotId());
            long started = delivery.stream().filter(CartonProgress::inProgress).count();
            long done = delivery.stream().filter(CartonProgress::finished).count();
            System.out.println("  delivery       " + delivery.size() + " cartons, "
                    + done + " done, " + started + " in progress");

            // Closing with cartons still unopened must be refused and must say which.
            UUID lotId = carton.lotId();
            try {
                backend.closeDelivery(lotId, false);
                System.out.println("PROBE FAILED  closed a delivery with cartons unopened");
                System.exit(1);
            } catch (BackendClient.RefusedException e) {
                System.out.println("  close refused  " + trim(e.getMessage()));
            }

            System.out.println("PROBE OK");
        } catch (BackendUnavailableException e) {
            System.out.println("PROBE FAILED  " + e.getMessage());
            System.exit(1);
        }
    }

    private static String trim(String text) {
        return text.length() <= 96 ? text : text.substring(0, 93) + "…";
    }
}
