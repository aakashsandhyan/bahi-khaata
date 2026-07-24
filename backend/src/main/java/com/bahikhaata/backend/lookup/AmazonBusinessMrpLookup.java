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
package com.bahikhaata.backend.lookup;

import com.bahikhaata.contracts.Money;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Looking up the printed price through the Amazon Business Product Search API — the official India
 * route, in place of scraping.
 *
 * <p>Authenticated with Login-with-Amazon: a long-lived refresh token, the app's client id and
 * secret, and the buyer's email, all read from the environment and never from the repository. The
 * refresh token is exchanged for a short-lived access token, cached until just before it expires.
 *
 * <p>{@code listPrice} is what Amazon shows struck through as M.R.P. — close, but one listing's
 * figure on one day, so it is treated as an estimate to confirm against the goods, exactly as every
 * looked-up price is. Batched at 30 ASINs a call and throttled to the documented half-a-second rate.
 *
 * <p>Selected by {@code bahikhaata.mrp.source=amazon-business}. The base URL, credentials, and — if
 * the account differs — the exact response shape are configuration, so nothing here is a secret and
 * the field paths can be corrected against a real response without touching the flow.
 */
@Component
@ConditionalOnProperty(name = "bahikhaata.mrp.source", havingValue = "amazon-business")
public class AmazonBusinessMrpLookup implements MrpLookup {

    private static final Logger log = LoggerFactory.getLogger(AmazonBusinessMrpLookup.class);
    private static final int MAX_PER_CALL = 30;
    private static final Duration THROTTLE = Duration.ofMillis(2000); // 0.5 requests per second

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String userEmail;
    private final String baseUrl;
    private final String tokenUrl;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    private volatile String accessToken;
    private volatile Instant accessTokenExpiry = Instant.MIN;

    AmazonBusinessMrpLookup(
            @Value("${bahikhaata.amazonBusiness.clientId:}") String clientId,
            @Value("${bahikhaata.amazonBusiness.clientSecret:}") String clientSecret,
            @Value("${bahikhaata.amazonBusiness.refreshToken:}") String refreshToken,
            @Value("${bahikhaata.amazonBusiness.userEmail:}") String userEmail,
            @Value("${bahikhaata.amazonBusiness.baseUrl:}") String baseUrl,
            @Value("${bahikhaata.amazonBusiness.tokenUrl:https://api.amazon.com/auth/o2/token}")
                    String tokenUrl) {
        this.clientId = trim(clientId);
        this.clientSecret = trim(clientSecret);
        this.refreshToken = trim(refreshToken);
        this.userEmail = trim(userEmail);
        this.baseUrl = trim(baseUrl).replaceAll("/+$", "");
        this.tokenUrl = trim(tokenUrl);
    }

    @Override
    public boolean isAvailable() {
        return !clientId.isEmpty()
                && !clientSecret.isEmpty()
                && !refreshToken.isEmpty()
                && !userEmail.isEmpty()
                && !baseUrl.isEmpty();
    }

    @Override
    public String unavailableReason() {
        List<String> missing = new ArrayList<>();
        if (clientId.isEmpty()) missing.add("clientId");
        if (clientSecret.isEmpty()) missing.add("clientSecret");
        if (refreshToken.isEmpty()) missing.add("refreshToken");
        if (userEmail.isEmpty()) missing.add("userEmail");
        if (baseUrl.isEmpty()) missing.add("baseUrl");
        return "Amazon Business lookup is not configured: missing " + String.join(", ", missing)
                + " (set bahikhaata.amazonBusiness.*).";
    }

    @Override
    public Map<String, Money> lookup(List<String> asins) {
        Map<String, Money> found = new HashMap<>();
        if (!isAvailable() || asins.isEmpty()) {
            return found;
        }
        String token;
        try {
            token = accessToken();
        } catch (Exception e) {
            log.warn("Amazon Business auth failed: {}", e.getMessage());
            return found;
        }
        boolean first = true;
        for (int i = 0; i < asins.size(); i += MAX_PER_CALL) {
            if (!first) {
                sleep(THROTTLE);
            }
            first = false;
            List<String> batch = asins.subList(i, Math.min(i + MAX_PER_CALL, asins.size()));
            try {
                found.putAll(fetch(batch, token));
            } catch (Exception e) {
                log.warn("Amazon Business lookup failed for {} ASINs: {}", batch.size(), e.getMessage());
            }
        }
        return found;
    }

    private Map<String, Money> fetch(List<String> asins, String token) throws Exception {
        String body =
                json.writeValueAsString(Map.of("productRegion", "IN", "asins", asins));
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/products/2020-08-26/products/getProductsByAsins"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("x-amz-access-token", token)
                        .header("x-amz-user-email", userEmail)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "getProductsByAsins returned " + response.statusCode() + ": " + response.body());
        }
        return parse(response.body());
    }

    /**
     * Pulls the printed price out of each product. Defensive by design: the response is walked for a
     * {@code listPrice} amount under the field names Amazon has used ({@code amount}, {@code value}),
     * so a small shape difference between accounts is a one-line change here, not a broken flow.
     */
    Map<String, Money> parse(String responseBody) throws Exception {
        Map<String, Money> found = new HashMap<>();
        JsonNode root = json.readTree(responseBody);
        JsonNode products = firstArray(root, "products", "items", "results");
        if (products == null) {
            return found;
        }
        for (JsonNode product : products) {
            String asin = text(product, "asin", "ASIN", "productId");
            Money mrp = money(product.get("listPrice"));
            if (asin != null && mrp != null) {
                found.put(asin, mrp);
            }
        }
        return found;
    }

    private Money money(JsonNode price) {
        if (price == null || price.isNull()) {
            return null;
        }
        JsonNode amount = price.get("amount");
        if (amount == null) amount = price.get("value");
        if (amount == null && price.isNumber()) amount = price;
        if (amount == null || !amount.isNumber()) {
            return null;
        }
        BigDecimal rupees = amount.decimalValue();
        return rupees.signum() > 0 ? Money.ofPaise(rupees.movePointRight(2).longValueExact()) : null;
    }

    private String accessToken() throws Exception {
        String cached = accessToken;
        if (cached != null && Instant.now().isBefore(accessTokenExpiry)) {
            return cached;
        }
        String form =
                "grant_type=refresh_token"
                        + "&refresh_token=" + enc(refreshToken)
                        + "&client_id=" + enc(clientId)
                        + "&client_secret=" + enc(clientSecret);
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(tokenUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "token exchange returned " + response.statusCode() + ": " + response.body());
        }
        JsonNode body = json.readTree(response.body());
        String token = body.path("access_token").asText(null);
        long expiresIn = body.path("expires_in").asLong(3600);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("token exchange returned no access_token");
        }
        this.accessToken = token;
        this.accessTokenExpiry = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));
        return token;
    }

    private static JsonNode firstArray(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node != null && node.isArray()) {
                return node;
            }
        }
        return root.isArray() ? root : null;
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isTextual()) {
                return value.asText();
            }
        }
        return null;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
