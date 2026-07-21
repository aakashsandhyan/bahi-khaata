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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Keepa as the source of a listed retail price.
 *
 * <p>Chosen over scraping Amazon directly. Scraping breaks on a layout change and gets blocked
 * at volume, and its failure mode is the dangerous one: it keeps working while quietly returning
 * the wrong number. A paid API costs rupees and fails loudly.
 *
 * <p>The key is read from the environment and never from the repository. It is a credential:
 * it is not committed, not logged, and not included in any error this throws.
 *
 * <p><strong>Unverified against the live API.</strong> The response shape below is written from
 * Keepa's documented format but has not been run against a real key, so the parsing is kept in
 * one small method that a single real response will confirm or correct. Treat
 * {@code LIST_PRICE_INDEX} and the domain code as the two things most likely to be wrong.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "bahikhaata.mrp.source", havingValue = "keepa")
public class KeepaMrpLookup implements MrpLookup {

    private static final Logger log = LoggerFactory.getLogger(KeepaMrpLookup.class);

    /** Keepa's numeric code for amazon.in. */
    private static final int DOMAIN_INDIA = 10;

    /**
     * Which of Keepa's price histories carries the listed retail price.
     *
     * <p>Keepa returns a {@code csv} array per product, one history per index. Index 4 is
     * documented as the list price — the struck-through figure Amazon India shows as M.R.P.
     */
    private static final int LIST_PRICE_INDEX = 4;

    /** Keepa accepts up to a hundred identifiers in one request. */
    private static final int BATCH_LIMIT = 100;

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    KeepaMrpLookup(@Value("${bahikhaata.keepa.key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isEmpty();
    }

    @Override
    public String unavailableReason() {
        return "No Keepa key is configured. Set BAHIKHAATA_KEEPA_KEY in the environment;"
                + " prices will be looked up on the next run. Unpacking is unaffected.";
    }

    @Override
    public Map<String, Money> lookup(List<String> asins) {
        Map<String, Money> found = new HashMap<>();
        if (!isAvailable() || asins.isEmpty()) {
            return found;
        }
        for (int from = 0; from < asins.size(); from += BATCH_LIMIT) {
            List<String> batch = asins.subList(from, Math.min(from + BATCH_LIMIT, asins.size()));
            try {
                found.putAll(fetch(batch));
            } catch (RuntimeException e) {
                // One failed batch must not lose the ones that worked, and must never surface
                // as a failure of goods-in. The message deliberately omits the key.
                log.warn("MRP lookup failed for {} products: {}", batch.size(), e.getMessage());
            }
        }
        return found;
    }

    private Map<String, Money> fetch(List<String> asins) {
        URI uri =
                URI.create(
                        "https://api.keepa.com/product?key="
                                + apiKey
                                + "&domain="
                                + DOMAIN_INDIA
                                + "&asin="
                                + String.join(",", asins));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Keepa answered HTTP " + response.statusCode());
            }
            return parse(response.body());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("could not reach Keepa", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while contacting Keepa", e);
        }
    }

    /**
     * Pulls the most recent list price out of Keepa's response.
     *
     * <p>Kept separate and package-visible so it can be tested against a captured response
     * without a key or a network. Keepa's histories are flat arrays of alternating timestamp and
     * price, in whole currency units, with -1 meaning "no data at that moment" — so the last
     * non-negative value is the current one.
     */
    Map<String, Money> parse(String body) {
        Map<String, Money> found = new HashMap<>();
        try {
            JsonNode products = json.readTree(body).path("products");
            for (JsonNode product : products) {
                String asin = product.path("asin").asText(null);
                JsonNode history = product.path("csv").path(LIST_PRICE_INDEX);
                if (asin == null || !history.isArray()) {
                    continue;
                }
                long latest = -1;
                for (int i = 1; i < history.size(); i += 2) {
                    long value = history.get(i).asLong(-1);
                    if (value >= 0) {
                        latest = value;
                    }
                }
                if (latest > 0) {
                    // Keepa reports whole rupees for the Indian store; paise is our unit.
                    found.put(asin, Money.ofPaise(latest * 100));
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Keepa returned something unreadable", e);
        }
        return found;
    }
}
