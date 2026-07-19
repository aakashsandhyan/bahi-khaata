package com.bahikhaata.terminal;

import com.bahikhaata.contracts.HealthResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

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
}
