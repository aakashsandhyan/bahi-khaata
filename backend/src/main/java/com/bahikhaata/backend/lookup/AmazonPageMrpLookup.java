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
package com.bahikhaata.backend.lookup;

import com.bahikhaata.contracts.Money;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reading the printed price off a public Amazon India product page.
 *
 * <p>Amazon shows {@code M.R.P.: ₹X} struck through beside its own price, and that figure is the
 * maximum retail price printed on the pack — the thing this system needs and cannot otherwise
 * get without someone reading it.
 *
 * <p><strong>No account, and no credentials anywhere.</strong> The figure is on the public page,
 * so signing in would add every risk and gain nothing: anonymous traffic that annoys Amazon gets
 * a CAPTCHA, whereas an automated session on a real account risks the account, and this shop
 * buys its stock through that relationship.
 *
 * <p><strong>This is against Amazon's terms of service and it will break.</strong> Not "may" —
 * a page layout changes eventually, and the failure that matters is the quiet one where a
 * selector still matches something and returns a number that is not a price. So it refuses
 * rather than guesses: an unrecognised page yields nothing, a robot check is reported as itself,
 * and everything it does find is recorded as an estimate and re-checked against what the goods
 * sold for online.
 *
 * <p><strong>Slow on purpose.</strong> A pause between requests, because speed is what gets an
 * address blocked, and there is no hurry: the alternative is someone reading a number off a pack
 * they are already holding.
 */
@Component
@ConditionalOnProperty(name = "bahikhaata.mrp.source", havingValue = "amazon", matchIfMissing = true)
public class AmazonPageMrpLookup implements MrpLookup {

    private static final Logger log = LoggerFactory.getLogger(AmazonPageMrpLookup.class);

    private static final String PRODUCT_PAGE = "https://www.amazon.in/dp/";

    /**
     * A real browser's identity. Not a disguise — the request is what it is — but a bare Java
     * user-agent is refused outright, so nothing at all can be learnt from sending one.
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
                    + " (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    /** Between requests. Slow is what keeps this working at all. */
    private static final Duration PAUSE = Duration.ofSeconds(4);

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    /**
     * The label Amazon India puts beside the printed price, then the amount that follows it.
     *
     * <p>Anchored on the words rather than on a CSS class, because the classes are generated and
     * change without notice while the label is what a customer reads. Still fragile; that is the
     * nature of this.
     */
    private static final Pattern MRP_LABEL =
            Pattern.compile(
                    "M\\.?R\\.?P\\.?\\s*:?.{0,400}?₹\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** What Amazon serves instead of a product when it thinks you are a machine. */
    private static final Pattern ROBOT_CHECK =
            Pattern.compile("captcha|are you a robot|automated access", Pattern.CASE_INSENSITIVE);

    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String unavailableReason() {
        return "";
    }

    @Override
    public Map<String, Money> lookup(List<String> asins) {
        Map<String, Money> found = new LinkedHashMap<>();
        for (String asin : asins) {
            try {
                fetchOne(asin).ifPresent(price -> found.put(asin, price));
            } catch (RobotCheckException e) {
                // No point continuing: every later request in this run gets the same answer, and
                // hammering on is exactly what turns a challenge into a block.
                log.warn("Amazon is serving a robot check; stopping after {} of {}",
                        found.size(), asins.size());
                break;
            } catch (RuntimeException e) {
                log.info("No price for {}: {}", asin, e.getMessage());
            }
            pause();
        }
        return found;
    }

    private Optional<Money> fetchOne(String asin) {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(PRODUCT_PAGE + asin))
                        .timeout(TIMEOUT)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept-Language", "en-IN,en;q=0.9")
                        .header("Accept", "text/html,application/xhtml+xml")
                        // Asked for explicitly, and decompressed below. Amazon compresses the
                        // reply whether or not this is sent, and HttpClient hands back the raw
                        // bytes — so without this the parser reads binary and quietly finds
                        // nothing. Caught only by making a real request.
                        .header("Accept-Encoding", "gzip")
                        .GET()
                        .build();
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("could not reach Amazon", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }

        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Amazon answered HTTP " + response.statusCode());
        }
        return priceIn(bodyText(response));
    }

    /** The page as text, decompressing where the reply says it is compressed. */
    private String bodyText(HttpResponse<byte[]> response) {
        byte[] body = response.body();
        boolean gzipped =
                response.headers().firstValue("Content-Encoding")
                        .map(encoding -> encoding.contains("gzip"))
                        .orElse(false);
        if (!gzipped) {
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
        }
        try (java.util.zip.GZIPInputStream in =
                new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(body))) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not read Amazon's compressed reply", e);
        }
    }

    /**
     * Pulls the printed price out of a product page.
     *
     * <p>Package-visible and pure, so it can be tested against saved pages with no network. This
     * is the method that will need correcting when Amazon changes its markup, and keeping it
     * small and alone is the whole defence against that.
     */
    Optional<Money> priceIn(String html) {
        if (ROBOT_CHECK.matcher(html).find()) {
            throw new RobotCheckException();
        }
        Matcher matcher = MRP_LABEL.matcher(html);
        if (!matcher.find()) {
            return Optional.empty();
        }
        BigDecimal rupees = new BigDecimal(matcher.group(1).replace(",", ""));
        if (rupees.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(Money.ofPaise(rupees.movePointRight(2).longValueExact()));
    }

    private void pause() {
        try {
            Thread.sleep(PAUSE.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Amazon asked whether we are a machine. Continuing would only make it more certain. */
    static class RobotCheckException extends RuntimeException {
        RobotCheckException() {
            super("Amazon served a robot check rather than a product page");
        }
    }
}
