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
package com.bahikhaata.backend.print;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * V44 seeds the shop's printer and its Devanagari name. This proves both survive a real boot — in
 * particular that the Devanagari literal in the migration is read as UTF-8 and not mangled, which a
 * bill printed straight to hardware would otherwise be the first to reveal.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-seed.db")
class ReceiptSeedMigrationTest {

    @Autowired private ReceiptPrinterConfigRepository printers;
    @Autowired private BillSettingsRepository billSettings;

    @Test
    void v44SeedsTheReceiptPrinter() {
        ReceiptPrinterConfig cfg =
                printers.findById(ReceiptPrinterConfig.SINGLETON_ID).orElseThrow();
        assertThat(cfg.getAddress()).isEqualTo("TVSE RP3200 Lite");
        assertThat(cfg.getTransport()).isEqualTo("USB");
        assertThat(cfg.isEnabled()).isTrue();
    }

    @Test
    void v44SeedsTheDevanagariShopName() {
        String name = billSettings.findById(BillSettings.SINGLETON_ID).orElseThrow().getShopName();

        // Mojibake (UTF-8 read as Latin-1) would land in U+0080..U+00FF or leave '?' fillers; a clean
        // UTF-8 read leaves every non-space character in the Devanagari block (U+0900..U+097F).
        assertThat(name).doesNotContain("?");
        boolean allDevanagari =
                name.codePoints().filter(c -> c != ' ').allMatch(c -> c >= 0x0900 && c <= 0x097F);
        assertThat(allDevanagari)
                .as("every character read back as Devanagari, so the UTF-8 seed decoded cleanly")
                .isTrue();
        // And it carries the nuqta the romanized spelling cannot — either decomposed (U+093C) or the
        // precomposed ja-with-nuqta (U+095B), depending on how the name was normalised.
        assertThat(name.codePoints().anyMatch(c -> c == 0x093C || c == 0x095B))
                .as("nuqta present (decomposed or precomposed)")
                .isTrue();
    }
}
