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
import org.springframework.stereotype.Service;

/**
 * Renders TSPL label documents for the shop's TSC TE-244 and its actual label stock.
 *
 * <p>TSPL, not ZPL: the TE-244 speaks TSPL natively — handed ZPL it raises no error and feeds a
 * blank label, which is exactly what the first live test printed. And the real stock is
 * <strong>2-up</strong>: an 82&nbsp;mm web carrying two 37.5 x 25&nbsp;mm labels side by side per
 * row. That shape drives two decisions here:
 *
 * <ul>
 *   <li>{@code SIZE} declares the full 82&nbsp;mm web, not one label. TSC printers centre the
 *       declared print area on the head, so declaring a single label's width landed the print
 *       straddling the middle of the web — half on each sticker. Declaring the whole web makes
 *       x&nbsp;=&nbsp;0 the web's left edge, and each column is then addressed by its offset.</li>
 *   <li>One rendered document is one <em>row</em> — the same label drawn in both columns — so no
 *       sticker is ever fed out blank. A caller wanting N copies prints ceil(N/2) rows
 *       ({@link #rowsFor}); an odd request yields one extra usable sticker, never a wasted one.</li>
 * </ul>
 *
 * <p>Text is ASCII-only ("Rs." not "₹", ".." not an ellipsis): the driver sends US-ASCII, which
 * silently turns anything else into "?". Fields are sanitized (quotes, newlines) because TSPL is a
 * raw command stream with no escaping of its own.
 */
@Service
public class LabelTemplateService {

    /** 203 dpi, the TE-244's native resolution — 8 dots per millimetre. */
    private static final int DOTS_PER_MM = 8;

    /** The full web as measured with a ruler on the actual roll: 2mm edge + 37.5mm label + 3mm
     * middle gap + 37.5mm label + 2mm edge — 82mm across. Declared exactly, because the printer
     * centres the declared area on the head: declaring the wrong width shifts every column
     * sideways by half the error, which is precisely what the first 2-up print showed. */
    private static final int WEB_WIDTH_MM = 82;
    /** Measured on the roll: 24mm tall, not the nominal 25 — the missing millimetre is why every
     * print registered high and shaved the barcode's top against the sticker's edge. */
    private static final int LABEL_HEIGHT_MM = 24;

    /** Where each column's content begins: the label's measured start plus a 1mm inner margin.
     * Left label spans 2..39.5mm, the right one 42.5..80mm. Dots, not mm, because the right
     * label's edge (42.5mm) is not a whole millimetre. */
    private static final int LEFT_X = 24;   // (2mm edge + 1mm margin) * 8 dots
    private static final int RIGHT_X = 348; // (42.5mm start + 1mm margin) * 8 dots

    /** How many physical stickers one rendered document produces. */
    public static final int LABELS_PER_ROW = 2;

    /** Rows to print for the asked-for copies — rounded up, so odd asks over-deliver, never blank. */
    public static int rowsFor(int copies) {
        return Math.max(1, (copies + LABELS_PER_ROW - 1) / LABELS_PER_ROW);
    }

    public String renderLabel(PrintLabelRequest request) throws PrinterDriver.PrinterException {
        return buildTspl(request);
    }

    private String buildTspl(PrintLabelRequest req) {
        StringBuilder t = new StringBuilder();
        t.append("SIZE ").append(WEB_WIDTH_MM).append("mm,").append(LABEL_HEIGHT_MM).append("mm\r\n");
        t.append("GAP 3mm,0mm\r\n");
        t.append("DIRECTION 1\r\n");
        t.append("CLS\r\n");
        column(t, req, LEFT_X);
        column(t, req, RIGHT_X);
        t.append("PRINT 1,1\r\n");
        return t.toString();
    }

    /**
     * One label's content, drawn at a column's x-origin. The vertical budget is 192 dots — the
     * measured 24mm, not the nominal 25 that had every print registering high. 20 dots (2.5mm) of
     * headroom before the bars, because the sensed edge still lands a shade late; then a 72-dot
     * barcode (9mm), its human-readable line to ~116, the name to ~138, and three small lines down
     * to 180, leaving 12 dots at the foot.
     */
    private void column(StringBuilder t, PrintLabelRequest req, int x) {
        // The one thing that must scan; trailing "1" prints the readable code beneath the bars.
        t.append("BARCODE ").append(x).append(",20,\"128\",72,1,0,2,2,\"")
                .append(sanitize(req.barcode())).append("\"\r\n");

        // Font "2" is 12 dots wide: 23 chars = 276 dots, inside the 284 usable dots.
        t.append("TEXT ").append(x).append(",118,\"2\",0,1,1,\"")
                .append(truncate(sanitize(req.productName()), 23)).append("\"\r\n");

        String line2 = truncate(sanitize(req.category()) + "  MRP Rs." + sanitize(req.mrpPaise()), 35);
        t.append("TEXT ").append(x).append(",142,\"1\",0,1,1,\"").append(line2).append("\"\r\n");

        String line3 =
                truncate("Cost Rs." + sanitize(req.costPerUnit()) + "  Lot " + sanitize(req.lotId()), 35);
        t.append("TEXT ").append(x).append(",156,\"1\",0,1,1,\"").append(line3).append("\"\r\n");

        String line4 = "Rec " + sanitize(req.receivedDate());
        if (req.expiryDate() != null && !req.expiryDate().isEmpty()) {
            line4 += "  Exp " + sanitize(req.expiryDate());
        }
        t.append("TEXT ").append(x).append(",170,\"1\",0,1,1,\"").append(truncate(line4, 35)).append("\"\r\n");
    }

    /**
     * Strips characters that would break the command's own quoting (a literal {@code "}) or inject
     * another line into the stream (a newline) — this is a raw device-control protocol, not
     * something with its own escaping, so an untrusted field must not be handed through unfiltered.
     */
    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "'").replace("\r", " ").replace("\n", " ");
    }

    private static String truncate(String value, int maxChars) {
        // ".." not the Unicode ellipsis: the driver sends bytes as US-ASCII, which would silently
        // turn a non-ASCII character into "?" — the same trap the rupee sign was swapped out of.
        return value.length() <= maxChars ? value : value.substring(0, maxChars - 2) + "..";
    }
}
