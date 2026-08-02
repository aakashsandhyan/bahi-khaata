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

import com.bahikhaata.contracts.Money;
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
    /** Declared one millimetre taller than the measured 24mm sticker: the frame registers with
     * usable slack at the foot, and the price's below-baseline pixels (curved digits dip under
     * their line) need rows past 192 to print instead of being clipped flat. The gap sensor still
     * registers each row, so the over-declaration rides on that slack. */
    private static final int LABEL_HEIGHT_MM = 25;
    private static final int LABEL_W = 304;      // rendered width, padded to a byte boundary
    private static final int LABEL_H = 200;
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
    private final Font mrpLabelFont;
    private final Font mrpFont;
    private final Font badgeFont;
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
        this.mrpLabelFont = new Font(Font.SANS_SERIF, Font.BOLD, 14);
        this.mrpFont = new Font(Font.SANS_SERIF, Font.BOLD, 21);
        this.badgeFont = new Font(Font.SANS_SERIF, Font.BOLD, 18);
        // A real rupee sign where the JVM's font carries the glyph; "Rs." where it does not.
        this.rupee = priceFont.canDisplay('₹') ? "₹" : "Rs.";
    }

    /** One label per column — the executor's normal case, where two queued jobs share a row. */
    public String renderRow(PrintLabelRequest left, PrintLabelRequest right) {
        StringBuilder t = new StringBuilder();
        t.append("SIZE ").append(WEB_WIDTH_MM).append("mm,").append(LABEL_HEIGHT_MM).append("mm\r\n");
        t.append("GAP 3mm,0mm\r\n");
        t.append("DIRECTION 1\r\n");
        // The frame registers ~1mm high on the sticker; shift the whole image down into that
        // slack. On this firmware a POSITIVE shift moves the image UP (the first live row lost its
        // top margins to a +8), so down is negative.
        t.append("SHIFT -8\r\n");
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
        // Everything, bars included, is the composed image — see the class note on why nothing is
        // left to the firmware's own rendering any more.
        appendBitmap(t, origin, composeColumn(req));
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

        // The barcode, encoded here rather than by the firmware: the printer's Code 128 encoder
        // compresses digit runs, so its rendered width never matched a width estimated outside it,
        // and the bars sat visibly off-centre. Encoding in-process makes the module count exact —
        // centring is arithmetic again, and the narrower subset-C bars buy real quiet zones.
        int[] widths = code128Widths(req.barcode() == null ? "" : req.barcode());
        int modules = 0;
        for (int w : widths) {
            modules += w;
        }
        int barsW = modules * 2;
        int bx = (300 - barsW) / 2;
        boolean bar = true;
        for (int w : widths) {
            if (bar) {
                g.fillRect(bx, BARS_Y, w * 2, BARS_H);
            }
            bx += w * 2;
            bar = !bar;
        }

        // Name: real metrics, truncated to what actually fits between the margins.
        g.setFont(nameFont);
        FontMetrics nm = g.getFontMetrics();
        String name = fit(req.productName() == null ? "" : req.productName(), nm, 300 - 2 * MARGIN);
        g.drawString(name, MARGIN, 138);

        // The deal, filling the foot. The price sits low, its foot aligned with the badge's.
        g.setFont(priceFont);
        FontMetrics pm = g.getFontMetrics();
        String price = rupee + rupees(req.pricePaise());
        // Baseline 187, not 190: round digits and the rupee sign overshoot the baseline by a few
        // pixels (curves dip below the line to look level), and at 190 those pixels fell past the
        // 192-row canvas — printed prices came out with flat-trimmed bottoms.
        g.drawString(price, MARGIN, 187);

        if (req.mrpPaise() != null && req.mrpPaise() > req.pricePaise()) {
            int clusterLeft = MARGIN + pm.stringWidth(price) + 10;
            // The cluster sits a shade in from the right edge — 3mm reads better than flush.
            int right = 300 - 24;

            // The reference format: a small "MRP" prefix (not struck), then the amount larger with
            // Indian digit grouping, the strike through the amount alone.
            g.setFont(mrpLabelFont);
            FontMetrics lm = g.getFontMetrics();
            g.setFont(mrpFont);
            FontMetrics mm = g.getFontMetrics();
            String amount = rupee + rupees(req.mrpPaise());
            int prefixW = lm.stringWidth("MRP") + 5;
            int lineW = prefixW + mm.stringWidth(amount);
            int lineX = Math.max(clusterLeft, right - lineW);
            g.setFont(mrpLabelFont);
            g.drawString("MRP", lineX, 159);
            g.setFont(mrpFont);
            g.drawString(amount, lineX + prefixW, 160);
            g.setStroke(new BasicStroke(2));
            g.drawLine(lineX + prefixW - 2, 153, lineX + prefixW + mm.stringWidth(amount) + 2, 153);

            // "SAVE 70%" as a reversed badge — white on solid black, a 1mm breath below the MRP.
            // Shared with the till (Money.percentOffTo) so the sticker and the counter agree.
            int percent = Money.ofPaise(req.mrpPaise()).percentOffTo(Money.ofPaise(req.pricePaise()));
            String save = "SAVE " + percent + "%";
            g.setFont(badgeFont);
            FontMetrics bm = g.getFontMetrics();
            int padX = 7;
            int badgeW = bm.stringWidth(save) + 2 * padX;
            int badgeH = 21;
            int badgeX = Math.max(clusterLeft, right - badgeW);
            int badgeY = 170;
            g.fillRect(badgeX, badgeY, badgeW, badgeH);
            g.setColor(Color.WHITE);
            g.drawString(save, badgeX + padX, badgeY + 16);
            g.setColor(Color.BLACK);
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

    /** Whole rupees where the paise are zero, two decimals otherwise — Indian digit grouping. */
    private static String rupees(long paise) {
        String whole = group(paise / 100);
        return paise % 100 == 0 ? whole : whole + String.format(".%02d", paise % 100);
    }

    /** Indian grouping: the last three digits, then pairs — 1,499 / 14,999 / 1,04,999. */
    private static String group(long value) {
        String s = String.valueOf(value);
        if (s.length() <= 3) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.substring(s.length() - 3));
        String head = s.substring(0, s.length() - 3);
        while (head.length() > 2) {
            out.insert(0, head.substring(head.length() - 2) + ",");
            head = head.substring(0, head.length() - 2);
        }
        return head + "," + out;
    }

    // --- Code 128, encoded in-process ---------------------------------------------------------

    /** The Code 128 module-width table: six widths per symbol, the stop pattern's seven last. */
    private static final String[] CODE128 = {
        "212222","222122","222221","121223","121322","131222","122213","122312","132212","221213",
        "221312","231212","112232","122132","122231","113222","123122","123221","223211","221132",
        "221231","213212","223112","312131","311222","321122","321221","312212","322112","322211",
        "212123","212321","232121","111323","131123","131321","112313","132113","132311","211313",
        "231113","231311","112133","112331","132131","113123","113321","133121","313121","211331",
        "231131","213113","213311","213131","311123","311321","331121","312113","312311","332111",
        "314111","221411","431111","111224","111422","121124","121421","141122","141221","112214",
        "112412","122114","122411","142112","142211","241211","221114","413111","241112","134111",
        "111242","121142","121241","114212","124112","124211","411212","421112","421211","212141",
        "214121","412121","111143","111341","131141","114113","114311","411113","411311","113141",
        "114131","311141","411131","211412","211214","211232","2331112"
    };

    /**
     * Encodes to bar/space widths: subset B, switching to subset C for even runs of four or more
     * digits — the same compression the firmware applies, which is why an outside width estimate
     * never matched. Characters outside Code 128 B are replaced with '-' rather than corrupting
     * the symbol.
     */
    private static int[] code128Widths(String value) {
        java.util.List<Integer> vals = new java.util.ArrayList<>();
        vals.add(104); // start B
        int i = 0;
        boolean inC = false;
        while (i < value.length()) {
            int run = 0;
            while (i + run < value.length() && Character.isDigit(value.charAt(i + run))) {
                run++;
            }
            if (run >= 4) {
                int pairs = run / 2;
                vals.add(inC ? -1 : 99); // switch to C (never already in C here)
                inC = true;
                for (int p = 0; p < pairs; p++) {
                    vals.add(Integer.parseInt(value.substring(i, i + 2)));
                    i += 2;
                }
                if (i < value.length() && !Character.isDigit(value.charAt(i)) || run % 2 == 1) {
                    vals.add(100); // back to B for the leftover
                    inC = false;
                }
            } else {
                char c = value.charAt(i++);
                if (c < 32 || c > 126) {
                    c = '-';
                }
                vals.add(c - 32);
            }
        }
        if (inC) {
            // ended in C: fine — checksum and stop follow directly
        }
        vals.removeIf(v -> v == -1);
        int checksum = vals.get(0);
        for (int k = 1; k < vals.size(); k++) {
            checksum += k * vals.get(k);
        }
        vals.add(checksum % 103);
        vals.add(106); // stop

        java.util.List<Integer> widths = new java.util.ArrayList<>();
        for (int v : vals) {
            for (char w : CODE128[v].toCharArray()) {
                widths.add(w - '0');
            }
        }
        return widths.stream().mapToInt(Integer::intValue).toArray();
    }
}
