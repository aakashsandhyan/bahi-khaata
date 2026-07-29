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
 * <strong>2-up</strong>: an 80&nbsp;mm web carrying two 38 x 25&nbsp;mm labels side by side per row.
 * That shape drives two decisions here:
 *
 * <ul>
 *   <li>{@code SIZE} declares the full 80&nbsp;mm web, not one label. TSC printers centre the
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

    /** The full web: two 38mm labels, a 2mm gap between them, 1mm edges — 80mm across. */
    private static final int WEB_WIDTH_MM = 80;
    private static final int LABEL_HEIGHT_MM = 25;

    /** Where each column's content begins: 1mm edge + 1mm inner margin, and the same past 41mm. */
    private static final int LEFT_X = 2 * DOTS_PER_MM;
    private static final int RIGHT_X = 42 * DOTS_PER_MM;

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
        t.append("GAP 2mm,0mm\r\n");
        t.append("DIRECTION 1\r\n");
        t.append("CLS\r\n");
        column(t, req, LEFT_X);
        column(t, req, RIGHT_X);
        t.append("PRINT 1,1\r\n");
        return t.toString();
    }

    /**
     * One label's content, drawn at a column's x-origin. The vertical budget is 200 dots (25mm):
     * barcode 4–68, its human-readable line to ~92 (which is why the name starts at 96 — the first
     * live print had them overlapping), name to 116, then three small lines ending at 164.
     */
    private void column(StringBuilder t, PrintLabelRequest req, int x) {
        // The one thing that must scan, and the minimum 8mm tall the label spec calls for;
        // trailing "1" prints the human-readable code beneath the bars.
        t.append("BARCODE ").append(x).append(",4,\"128\",64,1,0,2,2,\"")
                .append(sanitize(req.barcode())).append("\"\r\n");

        // Font "2" is 12 dots wide: 24 chars = 288 dots, inside the 304-dot label. The first live
        // print used a wider font whose line overran the label's edge.
        t.append("TEXT ").append(x).append(",96,\"2\",0,1,1,\"")
                .append(truncate(sanitize(req.productName()), 24)).append("\"\r\n");

        String line2 = truncate(sanitize(req.category()) + "  MRP Rs." + sanitize(req.mrpPaise()), 35);
        t.append("TEXT ").append(x).append(",120,\"1\",0,1,1,\"").append(line2).append("\"\r\n");

        String line3 =
                truncate("Cost Rs." + sanitize(req.costPerUnit()) + "  Lot " + sanitize(req.lotId()), 35);
        t.append("TEXT ").append(x).append(",136,\"1\",0,1,1,\"").append(line3).append("\"\r\n");

        String line4 = "Rec " + sanitize(req.receivedDate());
        if (req.expiryDate() != null && !req.expiryDate().isEmpty()) {
            line4 += "  Exp " + sanitize(req.expiryDate());
        }
        t.append("TEXT ").append(x).append(",152,\"1\",0,1,1,\"").append(truncate(line4, 35)).append("\"\r\n");
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
