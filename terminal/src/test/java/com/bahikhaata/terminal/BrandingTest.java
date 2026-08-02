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
package com.bahikhaata.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The bundled font must exist, parse, and actually cover the glyphs the brand name needs.
 *
 * <p>Verified through {@link java.awt.Font} rather than JavaFX, because JavaFX cannot start
 * a graphics toolkit in a headless build, and {@code canDisplayUpTo} answers the question
 * that matters — a font can load and still render the name as empty boxes if it lacks the
 * Devanagari block. The bytes on disk are the same either way, so this proves the resource
 * ships and covers the name.
 */
class BrandingTest {

    // U+095B, ja with nuqta — the character romanization drops. Held as an escape so this
    // test file stays pure ASCII and the assertion cannot be broken by an editor
    // normalizing the combining sequence.
    private static final String NUQTA_JA = "\u091C\u093C"; // ja + combining nuqta

    @Test
    @DisplayName("The font resource is bundled and parses")
    void fontResourceLoads() throws Exception {
        try (InputStream in = Branding.class.getResourceAsStream("/fonts/NotoSansDevanagari.ttf")) {
            assertThat(in).as("bundled font resource").isNotNull();
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            assertThat(font.getFamily()).containsIgnoringCase("Devanagari");
        }
    }

    @Test
    @DisplayName("The font covers every glyph in the Devanagari brand name")
    void fontCoversTheBrandName() throws Exception {
        try (InputStream in = Branding.class.getResourceAsStream("/fonts/NotoSansDevanagari.ttf")) {
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);

            // canDisplayUpTo returns -1 when every character is renderable. A font lacking
            // the nuqta would return the index of that character instead, and the name
            // would show as a missing-glyph box on a real screen.
            int firstUnrenderable = font.canDisplayUpTo(Branding.NAME_DEVANAGARI);

            assertThat(firstUnrenderable)
                    .as("font cannot render the brand name from index %d", firstUnrenderable)
                    .isEqualTo(-1);
        }
    }

    @Test
    @DisplayName("The nuqta the romanized spelling loses is present in the Devanagari name")
    void devanagariNameCarriesTheNuqta() {
        // The shop name is Hindi. Romanized "Baazar" cannot carry the nuqta on ja; the
        // Devanagari form must. Guards against the name being silently retyped without it.
        assertThat(Branding.NAME_DEVANAGARI).contains(NUQTA_JA);
        assertThat(Branding.NAME_ROMANIZED).isEqualTo("Bachat Bazaar");
    }
}
