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

import com.bahikhaata.contracts.SaleLineView;
import com.bahikhaata.contracts.SaleView;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Renders a completed sale as a bill for an 80mm thermal receipt printer.
 *
 * <p>The layout is built once as a list of styled {@link Row}s, then encoded either to ESC/POS
 * bytes for the printer ({@link #render}) or to plain text for a preview ({@link #renderText}), so
 * the two can never drift.
 *
 * <p>The fixed text — shop name, GSTIN, bill title, declaration — comes from {@link BillSettings},
 * so the shop's tax treatment (a composition Bill of Supply for now) is a settings change, not
 * code. No tax is printed: a composition dealer collects none, and the total is simply the sum of
 * the line prices. Dates print in the shop's timezone (IST).
 */
@Service
public class ReceiptTemplateService {

    /** Characters per line for an 80mm roll in Font A. */
    static final int WIDTH = 48;

    // size: 0 normal, 1 double height, 2 double width+height. raster: draw as a bitmap image instead
    // of text (for Devanagari and other scripts the printer has no font for — see ReceiptRaster).
    private record Row(String text, boolean center, boolean bold, int size, boolean raster) {
        Row(String text, boolean center, boolean bold, int size) {
            this(text, center, bold, size, false);
        }

        static Row of(String t) {
            return new Row(t, false, false, 0);
        }
    }

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("dd-MMM-uuuu  HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    private final BillSettingsRepository settings;

    ReceiptTemplateService(BillSettingsRepository settings) {
        this.settings = settings;
    }

    /** The bill as ESC/POS bytes, ready to send to the receipt printer. */
    public byte[] render(SaleView sale) {
        Escpos e = new Escpos();
        e.init();
        for (Row row : layout(sale)) {
            if (row.raster()) {
                // The shop name in a script the printer has no font for — printed as an image.
                e.image(ReceiptRaster.escposImage(row.text()));
            } else {
                e.style(row.center(), row.bold(), row.size());
                e.line(row.text());
            }
        }
        e.reset();
        e.feed(3);
        e.cut();
        return e.bytes();
    }

    /** The bill as plain text — for a preview or a test, exactly the lines the printer would show. */
    public String renderText(SaleView sale) {
        StringBuilder sb = new StringBuilder();
        for (Row row : layout(sale)) {
            sb.append(row.center() ? center(row.text()) : row.text()).append('\n');
        }
        return sb.toString();
    }

    private List<Row> layout(SaleView sale) {
        BillSettings s = settings.findById(BillSettings.SINGLETON_ID).orElseThrow();
        List<Row> rows = new ArrayList<>();

        // Devanagari (or any non-ASCII) shop name can't be set as text on a thermal printer, so it is
        // drawn as a centred bitmap; a Latin name stays fast double-height text.
        rows.add(new Row(s.getShopName(), true, true, 2, ReceiptRaster.needsRaster(s.getShopName())));
        if (notBlank(s.getAddress())) {
            rows.add(new Row(s.getAddress(), true, false, 0));
        }
        if (notBlank(s.getGstin())) {
            rows.add(new Row("GSTIN: " + s.getGstin(), true, false, 0));
        }
        rows.add(new Row(s.getBillTitle(), true, true, 0));

        rows.add(Row.of(rule()));
        rows.add(Row.of(lr("Bill: " + sale.billNoFormatted(), WHEN.format(sale.createdAt()))));
        if (notBlank(sale.operatorName())) {
            rows.add(Row.of("Cashier: " + sale.operatorName()));
        }
        rows.add(Row.of(rule()));

        for (SaleLineView l : sale.lines()) {
            rows.add(Row.of(clip(l.name(), WIDTH)));
            rows.add(Row.of(lr("  " + l.quantity() + " x " + rupees(l.unitPricePaise()),
                    rupees(l.lineTotalPaise()))));
        }

        rows.add(Row.of(rule()));
        if (sale.savingPaise() > 0) {
            rows.add(Row.of(lr("You saved", rupees(sale.savingPaise()))));
        }
        rows.add(new Row(lr("TOTAL", rupees(sale.totalPaise())), false, true, 1));
        rows.add(Row.of("Paid: " + sale.paymentMethod()));
        rows.add(Row.of(rule()));

        if (notBlank(s.getDeclaration())) {
            for (String w : wrap(s.getDeclaration(), WIDTH)) {
                rows.add(new Row(w, true, false, 0));
            }
        }
        if (notBlank(s.getFooter())) {
            rows.add(new Row(s.getFooter(), true, false, 0));
        }
        return rows;
    }

    /** Money as rupees, grouped, two decimals — never floating point (paise are integer). */
    static String rupees(long paise) {
        long rupees = paise / 100;
        long fraction = Math.abs(paise % 100);
        return String.format("%,d.%02d", rupees, fraction);
    }

    /** Left text and right text on one {@link #WIDTH}-wide line, right-justified; left clipped if long. */
    static String lr(String left, String right) {
        int pad = WIDTH - left.length() - right.length();
        if (pad < 1) {
            left = clip(left, Math.max(0, WIDTH - right.length() - 1));
            pad = Math.max(1, WIDTH - left.length() - right.length());
        }
        return left + " ".repeat(pad) + right;
    }

    private static String rule() {
        return "-".repeat(WIDTH);
    }

    private static String center(String text) {
        if (text.length() >= WIDTH) {
            return text;
        }
        int left = (WIDTH - text.length()) / 2;
        return " ".repeat(left) + text;
    }

    private static String clip(String text, int width) {
        return text.length() <= width ? text : text.substring(0, width);
    }

    /** Word-wrap to at most {@code width} characters per line. */
    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (cur.length() > 0 && cur.length() + 1 + word.length() > width) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) {
                cur.append(' ');
            }
            cur.append(word.length() > width ? clip(word, width) : word);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** A tiny ESC/POS byte builder — just the commands this bill needs. */
    private static final class Escpos {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private void raw(int... bytes) {
            for (int b : bytes) {
                out.write(b);
            }
        }

        void init() {
            raw(0x1B, 0x40); // ESC @ — reset
        }

        void style(boolean center, boolean bold, int size) {
            raw(0x1B, 0x61, center ? 1 : 0); // ESC a — alignment
            raw(0x1B, 0x45, bold ? 1 : 0); // ESC E — bold
            int gs = size == 2 ? 0x11 : size == 1 ? 0x01 : 0x00; // GS ! — height (and width at 2)
            raw(0x1D, 0x21, gs);
        }

        void reset() {
            style(false, false, 0);
        }

        void line(String text) {
            byte[] b = text.getBytes(StandardCharsets.US_ASCII);
            out.write(b, 0, b.length);
            out.write(0x0A);
        }

        /** Writes a pre-built raster block (GS v 0), centred, followed by a line feed. */
        void image(byte[] gsv0) {
            raw(0x1B, 0x61, 1); // ESC a — centre (the image is full width, so this is belt-and-braces)
            out.write(gsv0, 0, gsv0.length);
            out.write(0x0A);
        }

        void feed(int lines) {
            for (int i = 0; i < lines; i++) {
                out.write(0x0A);
            }
        }

        void cut() {
            raw(0x1D, 0x56, 0x42, 0x00); // GS V B 0 — partial cut with feed
        }

        byte[] bytes() {
            return out.toByteArray();
        }
    }
}
