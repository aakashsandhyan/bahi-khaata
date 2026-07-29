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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/**
 * Renders the shop's one label for the TSC TE-244, two per row on the 2-up roll.
 *
 * <p>Everything except the barcode is composed server-side into a 1-bit image and sent with the
 * {@code BITMAP} command; only the Code 128 stays a native TSPL {@code BARCODE}, where the
 * printer's own rendering gives the crispest scannable bars. Two live prints forced this design:
 * the firmware's built-in fonts are wider than their documented sizes — at more than one size — so
 * any layout computed from those documents overran the sticker's edge. A bitmap has no such lies:
 * every pixel is measured here, with real font metrics, before it is sent. It also means the label
 * can carry what the printer fonts never could — the Devanagari wordmark and a real "₹".
 *
 * <p>One rendered document is one <em>row</em> of the measured stock — an 82mm web, two
 * 37.5 x 24mm labels — and the two columns may carry <em>different</em> labels: the print queue
 * pairs jobs up so no sticker feeds out blank, and an odd last label is held for a partner.
 */
@Service
public class LabelTemplateService {

    /** The web and columns as measured with a ruler on the actual roll (203 dpi, 8 dots/mm). */
    private static final int WEB_WIDTH_MM = 82;
    private static final int LABEL_HEIGHT_MM = 24;
    private static final int LABEL_W = 304;      // rendered width, padded to a byte boundary
    private static final int LABEL_H = 192;
    private static final int LEFT_ORIGIN = 16;   // 2mm edge
    private static final int RIGHT_ORIGIN = 340; // the right label starts at 42.5mm
    private static final int MARGIN = 8;         // 1mm inner margin

    // The barcode band is drawn natively by the printer; the bitmap leaves it blank.
    private static final int BARS_Y = 56;
    private static final int BARS_H = 62;

    /** How many physical stickers one rendered document produces. */
    public static final int LABELS_PER_ROW = 2;

    /** Rows for the asked-for copies — rounded up, so odd asks over-deliver, never print blank. */
    public static int rowsFor(int copies) {
        return Math.max(1, (copies + LABELS_PER_ROW - 1) / LABELS_PER_ROW);
    }

    private final BufferedImage wordmark;
    private final Font nameFont;
    private final Font priceFont;
    private final Font mrpFont;
    private final Font offFont;
    private final String rupee;

    public LabelTemplateService() {
        try (InputStream in = getClass().getResourceAsStream("/print/wordmark.png")) {
            if (in == null) {
                throw new IllegalStateException("print/wordmark.png missing from the jar");
            }
            this.wordmark = ImageIO.read(in);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read print/wordmark.png", e);
        }
        this.nameFont = new Font(Font.SANS_SERIF, Font.BOLD, 22);
        this.priceFont = new Font(Font.SANS_SERIF, Font.BOLD, 34);
        this.mrpFont = new Font(Font.SANS_SERIF, Font.BOLD, 20);
        this.offFont = new Font(Font.SANS_SERIF, Font.BOLD, 22);
        // A real rupee sign where the JVM's font carries the glyph; "Rs." where it does not.
        this.rupee = priceFont.canDisplay('₹') ? "₹" : "Rs.";
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

    private void column(StringBuilder t, PrintLabelRequest req, int origin) {
        // The composed image first, then the native barcode over its reserved blank band.
        appendBitmap(t, origin, composeColumn(req));

        int barsWidth = (req.barcode().length() * 11 + 35) * 2;
        int barsX = origin + Math.max(0, (300 - barsWidth) / 2);
        t.append("BARCODE ").append(barsX).append(',').append(BARS_Y)
                .append(",\"128\",").append(BARS_H).append(",0,0,2,2,\"")
                .append(sanitizeBarcode(req.barcode())).append("\"\r\n");
    }

    /**
     * Composes everything but the barcode as pixels. Vertical plan (192 dots): wordmark 14–50,
     * the barcode's blank band 56–118, name 124–148, and the deal filling the foot to ~188 — the
     * price large on the left, the struck MRP and the saving stacked to its right.
     */
    private BufferedImage composeColumn(PrintLabelRequest req) {
        BufferedImage img = new BufferedImage(LABEL_W, LABEL_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, LABEL_W, LABEL_H);
        g.setColor(Color.BLACK);
        // No antialiasing: the output is 1-bit, and grey halos threshold into ragged edges.
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // Wordmark, centred at the top.
        g.drawImage(wordmark, (300 - wordmark.getWidth()) / 2, 14, null);

        // Name: real metrics, truncated to what actually fits between the margins.
        g.setFont(nameFont);
        FontMetrics nm = g.getFontMetrics();
        String name = fit(req.productName() == null ? "" : req.productName(), nm, 300 - 2 * MARGIN);
        g.drawString(name, MARGIN, 146);

        // The deal, filling the foot. Price first, at the left.
        g.setFont(priceFont);
        FontMetrics pm = g.getFontMetrics();
        String price = rupee + rupees(req.pricePaise());
        g.drawString(price, MARGIN, 186);

        if (req.mrpPaise() != null && req.mrpPaise() > req.pricePaise()) {
            int clusterLeft = MARGIN + pm.stringWidth(price) + 10;
            int right = 300 - MARGIN;

            // Struck MRP, right-aligned in the space left of the price.
            g.setFont(mrpFont);
            FontMetrics mm = g.getFontMetrics();
            String mrp = "MRP " + rupee + rupees(req.mrpPaise());
            int mrpW = Math.min(mm.stringWidth(mrp), right - clusterLeft);
            int mrpX = right - mm.stringWidth(mrp);
            if (mrpX < clusterLeft) {
                mrpX = clusterLeft; // never collide with the price; clip at the edge instead
            }
            g.drawString(mrp, mrpX, 164);
            g.setStroke(new BasicStroke(3));
            g.drawLine(mrpX - 2, 157, mrpX + mrpW + 2, 157);

            // The saving, right-aligned beneath the struck MRP.
            long percent = (req.mrpPaise() - req.pricePaise()) * 100 / req.mrpPaise();
            g.setFont(offFont);
            FontMetrics om = g.getFontMetrics();
            String off = percent + "% OFF";
            g.drawString(off, right - om.stringWidth(off), 188);
        }

        g.dispose();
        return img;
    }

    /** Truncates with ".." until the string measures inside the given pixel width. */
    private static String fit(String value, FontMetrics fm, int maxWidth) {
        String s = value.replace("\r", " ").replace("\n", " ");
        if (fm.stringWidth(s) <= maxWidth) {
            return s;
        }
        while (s.length() > 1 && fm.stringWidth(s + "..") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    /** The image as a TSPL BITMAP at the column origin: bit 0 prints, bit 1 stays blank. */
    private static void appendBitmap(StringBuilder t, int originX, BufferedImage img) {
        int widthBytes = img.getWidth() / 8;
        t.append("BITMAP ").append(originX).append(",0,").append(widthBytes).append(',')
                .append(img.getHeight()).append(",0,");
        for (int y = 0; y < img.getHeight(); y++) {
            for (int bx = 0; bx < widthBytes; bx++) {
                int b = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int rgb = img.getRGB(bx * 8 + bit, y);
                    int lum = ((rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF)) / 3;
                    if (lum >= 128) {
                        b |= (1 << (7 - bit)); // white -> 1 -> unprinted
                    }
                }
                t.append((char) b);
            }
        }
        t.append("\r\n");
    }

    /** Whole rupees where the paise are zero, two decimals otherwise. */
    private static String rupees(long paise) {
        return paise % 100 == 0
                ? String.valueOf(paise / 100)
                : String.format("%d.%02d", paise / 100, paise % 100);
    }

    /** A barcode value rides inside the BARCODE command's quotes — keep it to safe characters. */
    private static String sanitizeBarcode(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "'").replace("\r", " ").replace("\n", " ");
    }
}
