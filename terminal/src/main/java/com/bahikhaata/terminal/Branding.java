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

import java.io.InputStream;
import javafx.scene.text.Font;

/**
 * The shop's name, and the Devanagari font that renders it.
 *
 * <p>The shop has one name — a Hindi name. It is written two ways: in its own Devanagari
 * script, {@code बचत बाज़ार}, and romanized as {@code Bachat Baazar}. The romanization is
 * the same name in Roman letters, not a translation or a second name, and it cannot carry
 * the nuqta in {@code ज़} — which is why customer-facing surfaces use the Devanagari.
 *
 * <p>The font is bundled rather than assumed present. A counter PC is not guaranteed to
 * have a Devanagari face installed, and a missing one renders the name as empty boxes —
 * the worst possible first impression for a shop whose name is the brand. Shipping the
 * font means the name looks identical on every machine.
 */
public final class Branding {

    /** The shop name in its own Devanagari script. Nuqta on {@code ज़} is deliberate. */
    public static final String NAME_DEVANAGARI = "बचत बाज़ार";

    /**
     * The same Hindi name romanized, for logs, window titles, and anywhere Devanagari
     * rendering is not guaranteed. Not a translation — the name is Hindi either way.
     */
    public static final String NAME_ROMANIZED = "Bachat Baazar";

    private static final String FONT_RESOURCE = "/fonts/NotoSansDevanagari.ttf";

    private Branding() {}

    /**
     * Loads the bundled Devanagari font at the given point size.
     *
     * @throws IllegalStateException if the font resource is missing or unreadable — a
     *     packaging fault that must fail loudly at startup rather than silently falling
     *     back to a system font that may not exist
     */
    public static Font devanagari(double size) {
        try (InputStream in = Branding.class.getResourceAsStream(FONT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Bundled font " + FONT_RESOURCE + " is missing from the terminal jar");
            }
            Font font = Font.loadFont(in, size);
            if (font == null) {
                throw new IllegalStateException(
                        "Bundled font " + FONT_RESOURCE + " could not be parsed by JavaFX");
            }
            return font;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read bundled font " + FONT_RESOURCE, e);
        }
    }
}
