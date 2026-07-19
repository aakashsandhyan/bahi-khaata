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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.contracts.HealthResponse;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises the client over real HTTP against a stub, rather than mocking the client.
 *
 * <p>The stub is the JDK's own {@link HttpServer} — no test dependency to keep current, and
 * the bytes on the wire are real, which is what catches a JSON shape the terminal cannot
 * actually read.
 *
 * <p>Note what this test cannot do: talk to the real backend. {@code terminal} must never
 * depend on {@code backend}, in tests or anywhere else, so end-to-end verification is a
 * manual run of both processes.
 */
class BackendClientTest {

    private HttpServer server;

    private String startStub(int statusCode, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/health",
                exchange -> {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(statusCode, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("A healthy backend is parsed into the contract type")
    void parsesHealthyResponse() throws IOException {
        String uri = startStub(200, "{\"status\":\"UP\",\"schemaVersion\":\"1\"}");

        HealthResponse health = new BackendClient(uri).health();

        assertThat(health.status()).isEqualTo(HealthResponse.Status.UP);
        assertThat(health.schemaVersion()).isEqualTo("1");
    }

    @Test
    @DisplayName("An unknown field does not break the terminal")
    void toleratesUnknownFields() throws IOException {
        // A backend one version ahead must not stop the counter working.
        String uri = startStub(200, "{\"status\":\"UP\",\"schemaVersion\":\"7\",\"newThing\":true}");

        assertThat(new BackendClient(uri).health().schemaVersion()).isEqualTo("7");
    }

    @Test
    @DisplayName("A 503 is treated as unavailable, not as a usable answer")
    void refusesServiceUnavailable() throws IOException {
        String uri = startStub(503, "{\"status\":\"DOWN\",\"schemaVersion\":null}");

        assertThatThrownBy(() -> new BackendClient(uri).health())
                .isInstanceOf(BackendUnavailableException.class)
                .hasMessageContaining("not ready")
                .hasMessageContaining("503");
    }

    @Test
    @DisplayName("Nothing listening reports unavailable rather than hanging")
    void reportsUnreachableBackend() {
        // Port 1 is reserved and never served; connection is refused immediately.
        assertThatThrownBy(() -> new BackendClient("http://127.0.0.1:1").health())
                .isInstanceOf(BackendUnavailableException.class)
                .hasMessageContaining("Could not reach the backend");
    }

    @Test
    @DisplayName("An unreadable body is reported, not swallowed")
    void reportsUnparseableResponse() throws IOException {
        String uri = startStub(200, "this is not json");

        assertThatThrownBy(() -> new BackendClient(uri).health())
                .isInstanceOf(BackendUnavailableException.class)
                .hasMessageContaining("cannot read");
    }
}
