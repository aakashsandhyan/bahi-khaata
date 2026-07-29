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
 * Renders TSPL label templates for barcode printing.
 *
 * <p>The TSC TE-244 speaks TSPL natively, not ZPL — sending it ZPL produces no error, but no
 * printed content either; the printer simply does not recognise the command stream and feeds a
 * blank label. TSPL is also a different shape of language: {@code SIZE}/{@code GAP} must state the
 * physical label up front (fixed here at 38 x 25&nbsp;mm, the stock loaded in the shop's printer),
 * and one call renders one self-contained document — {@code CLS} through {@code PRINT 1,1} — so
 * printing N copies is still just repeating this whole string N times, exactly as the driver
 * already does; nothing about how a job is sent had to change.
 *
 * <p>At 38 x 25&nbsp;mm the canvas is small (304 x 200 dots at 203&nbsp;dpi), so every field from the
 * request is kept but laid out compactly rather than at the old ZPL layout's size — that layout was
 * drawn for a much larger label and its positions would have fallen off this one regardless of
 * language. The rupee sign is written as "Rs." rather than "₹": the driver sends bytes as US-ASCII,
 * which silently turns any non-ASCII character into "?", and a printer's built-in bitmap fonts are
 * unlikely to carry the glyph either.
 */
@Service
public class LabelTemplateService {

    /** 203 dpi, the TE-244's native resolution — 8 dots per millimetre. */
    private static final int DOTS_PER_MM = 8;
    private static final int LABEL_WIDTH_MM = 38;
    private static final int LABEL_HEIGHT_MM = 25;

    public String renderLabel(PrintLabelRequest request) throws PrinterDriver.PrinterException {
        return buildTspl(request);
    }

    private String buildTspl(PrintLabelRequest req) {
        StringBuilder t = new StringBuilder();
        t.append("SIZE ").append(LABEL_WIDTH_MM).append("mm,").append(LABEL_HEIGHT_MM).append("mm\r\n");
        t.append("GAP 2mm,0mm\r\n");
        t.append("DIRECTION 1\r\n");
        t.append("CLS\r\n");

        // Barcode first and largest — the one thing that must scan. Height 64 dots = 8mm, the
        // minimum this shop's label spec calls for; "1" prints the human-readable code beneath it.
        t.append("BARCODE 8,4,\"128\",64,1,0,2,2,\"").append(sanitize(req.barcode())).append("\"\r\n");

        t.append("TEXT 8,84,\"3\",0,1,1,\"").append(truncate(sanitize(req.productName()), 22)).append("\"\r\n");

        String line2 = truncate(sanitize(req.category()) + "  MRP Rs." + sanitize(req.mrpPaise()), 34);
        t.append("TEXT 8,108,\"1\",0,1,1,\"").append(line2).append("\"\r\n");

        String line3 =
                truncate("Cost Rs." + sanitize(req.costPerUnit()) + "  Lot " + sanitize(req.lotId()), 34);
        t.append("TEXT 8,122,\"1\",0,1,1,\"").append(line3).append("\"\r\n");

        String line4 = "Rec " + sanitize(req.receivedDate());
        if (req.expiryDate() != null && !req.expiryDate().isEmpty()) {
            line4 += "  Exp " + sanitize(req.expiryDate());
        }
        t.append("TEXT 8,136,\"1\",0,1,1,\"").append(truncate(line4, 34)).append("\"\r\n");

        t.append("PRINT 1,1\r\n");
        return t.toString();
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
