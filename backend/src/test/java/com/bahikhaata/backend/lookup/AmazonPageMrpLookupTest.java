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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.contracts.Money;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reading the printed price off an Amazon page.
 *
 * <p>Tested against markup Amazon actually sent rather than markup imagined here. That
 * distinction is the whole point: this code reads someone else's page and will break when they
 * change it, so the fixture is a tripwire. When Amazon rearranges things, this test fails and
 * somebody fixes the pattern — instead of prices quietly ceasing to be found.
 *
 * <p>No network and no credentials, so it runs anywhere the build does.
 */
class AmazonPageMrpLookupTest {

    private final AmazonPageMrpLookup lookup = new AmazonPageMrpLookup();

    private String realPage() throws IOException {
        try (var in = getClass().getResourceAsStream("/lookup/amazon-product-page.html")) {
            assertThat(in).as("the captured page fixture").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("The printed price is read from a page Amazon really sent")
    void readsTheRealPage() throws IOException {
        assertThat(lookup.priceIn(realPage()))
                .as("captured from a live product page: M.R.P: ₹799.00")
                .contains(Money.ofPaise(79_900));
    }

    @Test
    @DisplayName("Thousands separators survive")
    void handlesThousands() {
        String html = "<span>M.R.P:</span> <span class=\"a-text-strike\">₹1,299.00</span>";
        assertThat(lookup.priceIn(html)).contains(Money.ofPaise(129_900));
    }

    @Test
    @DisplayName("A page with no printed price yields nothing rather than a guess")
    void noPriceMeansNothing() {
        assertThat(lookup.priceIn("<html><body>Currently unavailable</body></html>"))
                .as("plenty of listings have no MRP shown; inventing one would be far worse")
                .isEmpty();
    }

    @Test
    @DisplayName("A robot check is reported as itself, not as an absent price")
    void robotCheckIsLoud() {
        assertThatThrownBy(
                        () -> lookup.priceIn(
                                "<html><body>Enter the characters you see below"
                                        + " (Sorry, we just need to make sure you're not a robot)"
                                        + " captcha</body></html>"))
                .as("carrying on after a challenge is what turns it into a block")
                .isInstanceOf(AmazonPageMrpLookup.RobotCheckException.class);
    }

    @Test
    @DisplayName("A price of zero is not a price")
    void zeroIsNotAPrice() {
        assertThat(lookup.priceIn("<span>M.R.P:</span> ₹0.00")).isEmpty();
    }
}
