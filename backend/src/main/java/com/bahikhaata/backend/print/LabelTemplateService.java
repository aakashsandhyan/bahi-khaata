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

import com.bahikhaata.contracts.PrintLabelRequest;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/**
 * Renders the shop's one label as TSPL for the TSC TE-244, two per row on the 2-up roll.
 *
 * <p>The label, top to bottom: the Devanagari wordmark, a centred Code 128 barcode, the product
 * name, and the price story — the shop's price large, and, when a confirmed MRP exists, that MRP
 * struck through with the saving percentage under it. No confirmed MRP means those two lines simply
 * do not print; an estimate never masquerades as the legal figure.
 *
 * <p>The wordmark is a pre-rendered 1-bit bitmap shipped in the jar and sent with the {@code
 * BITMAP} command, because the printer's own fonts are ASCII bitmap fonts and cannot draw
 * Devanagari. Everything else is ASCII ("Rs.", ".."): the driver sends ISO-8859-1, and the fonts
 * carry no rupee glyph.
 *
 * <p>One rendered document is one <em>row</em> of the measured stock — an 82mm web, two 37.5 x 24mm
 * labels — and the two columns may carry <em>different</em> labels: the print queue pairs jobs up so
 * no sticker feeds out blank, and an odd last label is held for a partner.
 */
@Service
public class LabelTemplateService {

    /** The web and columns as measured with a ruler on the actual roll (203 dpi, 8 dots/mm). */
    private static final int WEB_WIDTH_MM = 82;
    private static final int LABEL_HEIGHT_MM = 24;
    private static final int LABEL_WIDTH_DOTS = 300;
    private static final int LEFT_ORIGIN = 16;   // 2mm edge
    private static final int RIGHT_ORIGIN = 340; // the right label starts at 42.5mm
    private static final int MARGIN = 8;         // 1mm inner margin

    /** How many physical stickers one rendered document produces. */
    public static final int LABELS_PER_ROW = 2;

    /** Rows for the asked-for copies — rounded up, so odd asks over-deliver, never print blank. */
    public static int rowsFor(int copies) {
        return Math.max(1, (copies + LABELS_PER_ROW - 1) / LABELS_PER_ROW);
    }

    private final String wordmarkBitmapData;
    private final int wordmarkWidthBytes;
    private final int wordmarkHeight;
    private final int wordmarkWidthDots;

    public LabelTemplateService() {
        // Load the wordmark once: PNG -> 1-bit rows in the BITMAP wire format.
        try (InputStream in = getClass().getResourceAsStream("/print/wordmark.png")) {
            if (in == null) {
                throw new IllegalStateException("print/wordmark.png missing from the jar");
            }
            BufferedImage img = ImageIO.read(in);
            this.wordmarkWidthDots = img.getWidth();
            this.wordmarkHeight = img.getHeight();
            this.wordmarkWidthBytes = (img.getWidth() + 7) / 8;
            StringBuilder data = new StringBuilder(wordmarkWidthBytes * wordmarkHeight);
            for (int y = 0; y < wordmarkHeight; y++) {
                for (int bx = 0; bx < wordmarkWidthBytes; bx++) {
                    int b = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = bx * 8 + bit;
                        boolean black = false;
                        if (x < img.getWidth()) {
                            int rgb = img.getRGB(x, y);
                            int lum = ((rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF)) / 3;
                            black = lum < 128;
                        }
                        // TSPL BITMAP: a 1 bit leaves the dot unprinted, a 0 bit prints it.
                        if (!black) {
                            b |= (1 << (7 - bit));
                        }
                    }
                    data.append((char) b);
                }
            }
            this.wordmarkBitmapData = data.toString();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read print/wordmark.png", e);
        }
    }

    /** One label per column — the executor's normal case, where two queued jobs share a row. */
    public String renderRow(PrintLabelRequest left, PrintLabelRequest right) {
        StringBuilder t = new StringBuilder();
        t.append("SIZE ").append(WEB_WIDTH_MM).append("mm,").append(LABEL_HEIGHT_MM).append("mm\r\n");
        t.append("GAP 3mm,0mm\r\n");
        t.append("DIRECTION 1\r\n");
        t.append("CLS\r\n");
        column(t, left, LEFT_ORIGIN);
        column(t, right, RIGHT_ORIGIN);
        t.append("PRINT 1,1\r\n");
        return t.toString();
    }

    /** The same label in both columns — a duplicate pair, used by test prints and queue flushes. */
    public String renderLabel(PrintLabelRequest request) {
        return renderRow(request, request);
    }

    /**
     * One label's content at a column origin. Vertical budget 192 dots: wordmark 10–46, bars
     * 54–110 (7mm), name 116–136, then the deal — MRP struck at 142 with the saving under it on
     * the right, and the price large on the left at 152.
     */
    private void column(StringBuilder t, PrintLabelRequest req, int origin) {
        // Wordmark, centred. BITMAP: x, y, width-in-bytes, height, mode 0 (overwrite), raw rows.
        int wmX = origin + (LABEL_WIDTH_DOTS - wordmarkWidthDots) / 2;
        t.append("BITMAP ").append(wmX).append(",10,").append(wordmarkWidthBytes).append(',')
                .append(wordmarkHeight).append(",0,").append(wordmarkBitmapData).append("\r\n");

        // Centred Code 128, no readable line — the bars are the identity. A 10-char BBZ code at
        // narrow 2 is ~290 dots wide, so "centred" is a small indent; shorter codes centre wider.
        int barsWidth = (req.barcode().length() * 11 + 35) * 2;
        int barsX = origin + Math.max(0, (LABEL_WIDTH_DOTS - barsWidth) / 2);
        t.append("BARCODE ").append(barsX).append(",54,\"128\",56,0,0,2,2,\"")
                .append(sanitize(req.barcode())).append("\"\r\n");

        // Name: font "2" is 12 dots wide — 23 chars inside the 284 usable dots.
        t.append("TEXT ").append(origin + MARGIN).append(",116,\"2\",0,1,1,\"")
                .append(truncate(sanitize(req.productName()), 23)).append("\"\r\n");

        if (req.mrpPaise() != null && req.mrpPaise() > req.pricePaise()) {
            // MRP struck through, right-aligned, font "2" so it reads at arm's length.
            String mrp = "MRP Rs." + rupees(req.mrpPaise());
            int mrpWidth = mrp.length() * 12;
            int mrpX = origin + LABEL_WIDTH_DOTS - MARGIN - mrpWidth;
            t.append("TEXT ").append(mrpX).append(",142,\"2\",0,1,1,\"").append(mrp).append("\"\r\n");
            // The strike: a 3-dot bar through the MRP's middle.
            t.append("BAR ").append(mrpX - 2).append(",151,").append(mrpWidth + 4).append(",3\r\n");

            long percent = (req.mrpPaise() - req.pricePaise()) * 100 / req.mrpPaise();
            String off = percent + "% OFF";
            int offX = origin + LABEL_WIDTH_DOTS - MARGIN - off.length() * 12;
            t.append("TEXT ").append(offX).append(",166,\"2\",0,1,1,\"").append(off).append("\"\r\n");
        }

        // The shop's price — the hero, font "3" (16 x 24).
        t.append("TEXT ").append(origin + MARGIN).append(",152,\"3\",0,1,1,\"")
                .append("Rs.").append(rupees(req.pricePaise())).append("\"\r\n");
    }

    /** Whole rupees where the paise are zero, two decimals otherwise. */
    private static String rupees(long paise) {
        return paise % 100 == 0
                ? String.valueOf(paise / 100)
                : String.format("%d.%02d", paise / 100, paise % 100);
    }

    /**
     * Strips characters that would break the command's own quoting (a literal {@code "}) or inject
     * another line into the stream (a newline) — TSPL is a raw command stream with no escaping.
     */
    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "'").replace("\r", " ").replace("\n", " ");
    }

    private static String truncate(String value, int maxChars) {
        // ".." not the Unicode ellipsis — the text must stay ASCII for the printer's fonts.
        return value.length() <= maxChars ? value : value.substring(0, maxChars - 2) + "..";
    }
}
