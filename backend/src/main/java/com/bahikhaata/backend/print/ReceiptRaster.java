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
package com.bahikhaata.backend.print;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Renders a line of text to an ESC/POS raster block, for scripts a thermal printer cannot set as
 * text. ESC/POS printers carry single-byte code pages (ASCII, CP437) and no Devanagari font, so the
 * shop name in Devanagari would print as {@code ????}. Drawn as a bitmap with a bundled Devanagari
 * font and sent as a {@code GS v 0} raster image, it prints on any ESC/POS printer regardless of its
 * fonts.
 *
 * <p>Only the header (the shop name) is ever rastered; the rest of the bill stays fast text.
 */
final class ReceiptRaster {

    /** Printable dots across an 80mm roll: 48 Font-A columns × 12 dots. Matches the text width. */
    private static final int WIDTH_DOTS = 576;

    /** Point size of the rendered header — prominent, roughly the double-height text it replaces. */
    private static final float HEADER_POINTS = 46f;

    /** The bundled Devanagari font, loaded once. Bold so the header reads at a glance on thermal. */
    private static final Font FONT = loadFont();

    private ReceiptRaster() {}

    private static Font loadFont() {
        try (InputStream in = ReceiptRaster.class.getResourceAsStream("/fonts/NotoSansDevanagari.ttf")) {
            if (in == null) {
                throw new IllegalStateException("Devanagari font not on the classpath: /fonts/NotoSansDevanagari.ttf");
            }
            return Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(Font.BOLD, HEADER_POINTS);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load the Devanagari font", e);
        }
    }

    /** True when text has any character a thermal printer cannot set as ASCII — the trigger to raster. */
    static boolean needsRaster(String text) {
        return text != null && text.chars().anyMatch(c -> c > 0x7F);
    }

    /**
     * The text as an ESC/POS {@code GS v 0} raster block, {@value #WIDTH_DOTS} dots wide with the text
     * centred. Antialiased then thresholded to 1-bit, which reads better on thermal than 1-bit
     * rasterisation. The caller writes these bytes straight to the printer.
     */
    static byte[] escposImage(String text) {
        // Measure first, on a throwaway 1×1 surface, to size the real one to the font's metrics.
        Graphics2D probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        probe.setFont(FONT);
        FontMetrics fm = probe.getFontMetrics();
        int ascent = fm.getAscent();
        int height = ascent + fm.getDescent() + 8; // a little vertical breathing room
        int textWidth = fm.stringWidth(text);
        probe.dispose();

        BufferedImage img = new BufferedImage(WIDTH_DOTS, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH_DOTS, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.setFont(FONT);
        int x = Math.max(0, (WIDTH_DOTS - textWidth) / 2);
        g.drawString(text, x, ascent + 4);
        g.dispose();

        return pack(img);
    }

    /** Bit-pack a black-on-white image into a {@code GS v 0} raster block (MSB-first, 1 = dot). */
    private static byte[] pack(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int bytesPerRow = (width + 7) / 8;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x1D);
        out.write(0x76);
        out.write(0x30);
        out.write(0x00); // m — normal (no scaling)
        out.write(bytesPerRow & 0xFF);
        out.write((bytesPerRow >> 8) & 0xFF);
        out.write(height & 0xFF);
        out.write((height >> 8) & 0xFF);

        for (int row = 0; row < height; row++) {
            for (int b = 0; b < bytesPerRow; b++) {
                int bits = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int px = b * 8 + bit;
                    if (px < width && (img.getRGB(px, row) & 0xFF) < 128) {
                        bits |= 0x80 >> bit; // dark pixel → printed dot
                    }
                }
                out.write(bits);
            }
        }
        return out.toByteArray();
    }
}
