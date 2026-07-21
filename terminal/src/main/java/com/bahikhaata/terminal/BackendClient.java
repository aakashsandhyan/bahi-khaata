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
import com.bahikhaata.contracts.DeliveryClosed;
import com.bahikhaata.contracts.DeliveryProgress;
import com.bahikhaata.contracts.HealthResponse;
import com.bahikhaata.contracts.SuggestedMrp;
import com.bahikhaata.contracts.UnpackingCarton;
import com.bahikhaata.contracts.UnpackingLine;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The terminal's only route to data.
 *
 * <p>Every read and write goes through here. The terminal holds no database and no
 * business logic, which is what keeps the backend detachable — moving it to a real server
 * when a second outlet exists is a change to {@code baseUri} and nothing else.
 *
 * <p>Timeouts are short because this is localhost. A request that has not answered in a
 * couple of seconds is not slow, it is broken, and the cashier needs to be told rather
 * than left watching a frozen window.
 */
public class BackendClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    static final String DEFAULT_BASE_URI = "http://127.0.0.1:8080";

    private final URI baseUri;
    private final HttpClient http;
    private final ObjectMapper json;

    public BackendClient(String baseUri) {
        this.baseUri = URI.create(Objects.requireNonNull(baseUri, "backend base URI"));
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.json =
                new ObjectMapper()
                        // A field the terminal does not know about is not a reason to fail a
                        // sale. Backend and terminal are versioned together today, but this
                        // keeps a rolling upgrade from breaking the counter.
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** Reads the base URI from {@code bahikhaata.backend.uri}, defaulting to localhost. */
    public static BackendClient fromSystemProperties() {
        return new BackendClient(System.getProperty("bahikhaata.backend.uri", DEFAULT_BASE_URI));
    }

    /**
     * Asks whether the backend can serve.
     *
     * @throws BackendUnavailableException if unreachable, timed out, or reporting itself
     *     unusable
     */
    public HealthResponse health() {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri.resolve("/api/health"))
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new BackendUnavailableException("Could not reach the backend at " + baseUri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackendUnavailableException("Interrupted while contacting the backend", e);
        }

        if (response.statusCode() != 200) {
            throw new BackendUnavailableException(
                    "Backend at " + baseUri + " is not ready (HTTP " + response.statusCode() + ")");
        }

        try {
            return json.readValue(response.body(), HealthResponse.class);
        } catch (IOException e) {
            throw new BackendUnavailableException(
                    "Backend at " + baseUri + " returned a response this terminal cannot read", e);
        }
    }

    // ---- unpacking -------------------------------------------------------------------
    //
    // A refusal from the backend is not a fault here: counting against a closed delivery, or
    // closing one with cartons unopened, are things the operator needs told plainly. They come
    // back as RefusedException carrying the backend's own sentence, which is written to be read
    // by the person at the counter rather than by a developer.

    /** Cartons bearing a scanned tracking number. Empty when the number is not this delivery's. */
    public List<UnpackingCarton> cartonsByTracking(String trackingNumber) {
        return getList(
                "/api/unpacking/boxes/by-tracking/" + encode(trackingNumber),
                UnpackingCarton.class);
    }

    /**
     * Which line a scanned code belongs to, or empty if the code is new.
     *
     * <p>Asked on every scan: a product gathers codes over time, and the one in someone's hand
     * may be any of them.
     */
    public java.util.Optional<UnpackingLine> resolveInCarton(UUID boxId, String code) {
        List<UnpackingLine> found =
                getList(
                        "/api/unpacking/boxes/" + boxId + "/resolve?code=" + encode(code),
                        UnpackingLine.class);
        return found.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(found.get(0));
    }

    /** What should be inside one carton, and how much of it has been found. */
    public List<UnpackingLine> linesIn(UUID boxId) {
        return getList("/api/unpacking/boxes/" + boxId + "/lines", UnpackingLine.class);
    }

    /** Every delivery, and how far each has been unpacked. */
    public List<DeliveryProgress> deliveries() {
        return getList("/api/unpacking/deliveries", DeliveryProgress.class);
    }

    /** What a lookup thinks these goods' printed price is. Offered, never applied. */
    public SuggestedMrp suggestedMrp(UUID lineId) {
        return get("/api/unpacking/lines/" + lineId + "/suggested-mrp", SuggestedMrp.class);
    }

    /** How far a delivery has been unpacked. */
    public DeliveryProgress deliveryProgress(UUID lotId) {
        return get("/api/unpacking/lots/" + lotId + "/progress", DeliveryProgress.class);
    }

    /** Every carton in a delivery, and where each has got to. */
    public List<CartonProgress> cartonsInDelivery(UUID lotId) {
        return getList("/api/unpacking/lots/" + lotId + "/boxes", CartonProgress.class);
    }

    /** Records units found against something the manifest named. */
    public CountOutcome count(
            UUID lineId, long quantity, Long mrpPaise, String condition, boolean mrpIsEstimate) {
        return post(
                "/api/unpacking/lines/" + lineId + "/count",
                body(
                        "quantity", quantity,
                        "mrpPaise", mrpPaise,
                        // Whether the figure was read off the pack or looked up. A looked-up
                        // price is evidence about the printed one, not the printed one, and a
                        // label built on it should be recognisable as such later.
                        "mrpIsEstimate", mrpIsEstimate,
                        "condition", condition),
                CountOutcome.class);
    }

    /**
     * Tags a line with the code printed on the goods, and counts one.
     *
     * <p>The manifest names goods by a marketplace identifier that is on no pack, so the first
     * time an item is scanned nothing matches. Someone holding it says which line it is, and
     * from then on the real code resolves by itself.
     */
    public CountOutcome tag(
            UUID lineId, String scannedCode, long quantity, Long mrpPaise, String condition,
            boolean mrpIsEstimate) {
        return post(
                "/api/unpacking/lines/" + lineId + "/tag",
                body(
                        "scannedCode", scannedCode,
                        "quantity", quantity,
                        "mrpPaise", mrpPaise,
                        "mrpIsEstimate", mrpIsEstimate,
                        "condition", condition),
                CountOutcome.class);
    }

    /** Records something found in a carton that the manifest does not mention. */
    public CountOutcome countUnlisted(
            UUID boxId, String code, String name, String categoryCode, long quantity,
            Long mrpPaise) {
        return post(
                "/api/unpacking/boxes/" + boxId + "/unlisted",
                body(
                        "code", code,
                        "name", name,
                        "categoryCode", categoryCode,
                        "quantity", quantity,
                        "mrpPaise", mrpPaise,
                        "mrpIsEstimate", false),
                CountOutcome.class);
    }

    /** Takes back a count, and unmaps the code if that scan taught it. */
    public void undo(UUID lineId, long quantity, String condition, String untagCode) {
        post(
                "/api/unpacking/lines/" + lineId + "/undo",
                body(
                        "quantity", quantity,
                        // Left out entirely when unknown, which means "whichever it was counted
                        // as" — what a row on screen knows is three counted, not how the three
                        // were split.
                        "condition", condition,
                        "untagCode", untagCode),
                Void.class);
    }

    /** Forgets a code, so the same sticker can be scanned onto the right item. */
    public void releaseCode(String code) {
        post("/api/unpacking/codes/" + encode(code) + "/release", body(), Void.class);
    }

    public void finishCarton(UUID boxId) {
        post("/api/unpacking/boxes/" + boxId + "/finish", body(), Void.class);
    }

    public void reopenCarton(UUID boxId) {
        post("/api/unpacking/boxes/" + boxId + "/reopen", body(), Void.class);
    }

    /** Finishes a delivery and settles what it cost. */
    public DeliveryClosed closeDelivery(UUID lotId, boolean confirmUnopened) {
        return post(
                "/api/unpacking/lots/" + lotId + "/close?confirm=" + confirmUnopened,
                body(),
                DeliveryClosed.class);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private <T> T get(String path, Class<T> type) {
        HttpResponse<String> response =
                send(HttpRequest.newBuilder(baseUri.resolve(path)).timeout(REQUEST_TIMEOUT).GET());
        requireOk(response);
        try {
            return json.readValue(response.body(), type);
        } catch (IOException e) {
            throw new BackendUnavailableException("Unreadable response from " + path, e);
        }
    }

    private <T> List<T> getList(String path, Class<T> element) {
        HttpResponse<String> response =
                send(HttpRequest.newBuilder(baseUri.resolve(path)).timeout(REQUEST_TIMEOUT).GET());
        if (response.statusCode() == 404) {
            return List.of();
        }
        requireOk(response);
        try {
            return json.readValue(
                    response.body(),
                    json.getTypeFactory().constructCollectionType(List.class, element));
        } catch (IOException e) {
            throw new BackendUnavailableException("Unreadable response from " + path, e);
        }
    }

    /**
     * A request body, omitting anything absent.
     *
     * <p>Map.of refuses null values, so absent fields were being sent as empty strings — which
     * looks harmless and is not: an empty string is not a valid enum, so the backend failed to
     * read the request at all and answered with a framework error before any of its own code
     * ran. A field that is not there should not be there.
     */
    private static Map<String, Object> body(Object... keysAndValues) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            Object value = keysAndValues[i + 1];
            if (value != null) {
                body.put((String) keysAndValues[i], value);
            }
        }
        return body;
    }

    private <T> T post(String path, Map<String, Object> body, Class<T> type) {
        String payload;
        try {
            payload = json.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException("could not build the request body", e);
        }
        HttpResponse<String> response =
                send(
                        HttpRequest.newBuilder(baseUri.resolve(path))
                                .timeout(REQUEST_TIMEOUT)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(payload)));
        requireOk(response);
        if (type == Void.class || response.body() == null || response.body().isBlank()) {
            return null;
        }
        try {
            return json.readValue(response.body(), type);
        } catch (IOException e) {
            throw new BackendUnavailableException("Unreadable response from " + path, e);
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new BackendUnavailableException("Could not reach the backend at " + baseUri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackendUnavailableException("Interrupted while contacting the backend", e);
        }
    }

    /**
     * Turns a refusal into something sayable.
     *
     * <p>400 and 409 carry a sentence the backend wrote for a person — that a delivery is
     * closed, that cartons are unopened. Passing it straight through beats inventing a
     * terminal-side paraphrase that will drift out of step with the rule it describes.
     */
    private void requireOk(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        if (status == 400 || status == 409) {
            throw new RefusedException(readMessage(response.body()));
        }
        throw new BackendUnavailableException(
                "Backend at " + baseUri + " answered HTTP " + status);
    }

    private String readMessage(String body) {
        if (body == null || body.isBlank()) {
            return "The backend refused that, without saying why.";
        }
        // A refusal this system wrote is a bare sentence meant for a person. One the framework
        // wrote is a JSON object about paths and timestamps, which is no use to somebody holding
        // a carton — so it is turned into something sayable rather than shown raw.
        try {
            var node = json.readTree(body);
            if (node.isObject()) {
                if (node.hasNonNull("message") && !node.get("message").asText().isBlank()) {
                    return node.get("message").asText();
                }
                return "The system could not do that, and did not say why. Tell the manager"
                        + " what you were doing.";
            }
        } catch (IOException ignored) {
            // Not JSON, so it is already the sentence.
        }
        return body;
    }

    /** The backend declined, and the reason is worth showing the operator verbatim. */
    public static class RefusedException extends RuntimeException {
        public RefusedException(String message) {
            super(message);
        }
    }
}
