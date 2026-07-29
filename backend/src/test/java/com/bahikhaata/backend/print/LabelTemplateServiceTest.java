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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LabelTemplateServiceTest {

    private final LabelTemplateService service = new LabelTemplateService();

    @Test
    void rendersLabelWithAllFields() throws PrinterDriver.PrinterException {
        PrintLabelRequest req = new PrintLabelRequest(
            "PROD-001",
            "Coconut Oil 1L",
            "Kitchen",
            "240",
            "45000",
            "LOT-2024-07",
            "2027-07-27",
            "2026-07-27"
        );

        String tspl = service.renderLabel(req);

        assertTrue(tspl.contains("SIZE 82mm,25mm"),
            "must declare the full 2-up web, not one label — the printer centres the declared area");
        assertTrue(tspl.contains("CLS"), "must clear the image buffer before drawing");
        assertTrue(tspl.contains("PRINT 1,1"), "must end by firing the print");
        assertTrue(tspl.contains("PROD-001"), "Barcode value must be in output");
        assertTrue(tspl.contains("Coconut Oil 1L"), "Product name must be in output");
        assertTrue(tspl.contains("Kitchen"), "Category must be in output");
        assertTrue(tspl.contains("240"), "Cost must be in output");
        assertTrue(tspl.contains("45000"), "MRP must be in output");
        assertTrue(tspl.contains("LOT-2024-07"), "Lot must be in output");
        assertTrue(tspl.contains("2027-07-27"), "Expiry must be in output");
        assertTrue(tspl.contains("2026-07-27"), "Received date must be in output");
    }

    @Test
    void rendersLabelWithoutExpiry() throws PrinterDriver.PrinterException {
        PrintLabelRequest req = new PrintLabelRequest(
            "PROD-002",
            "Face Cream",
            "Personal Care",
            "120",
            "29900",
            "LOT-2024-08",
            "",
            "2026-07-27"
        );

        String tspl = service.renderLabel(req);

        assertTrue(tspl.contains("CLS"), "must clear the image buffer before drawing");
        assertTrue(tspl.contains("Face Cream"), "Product name must be in output");
        assertFalse(tspl.contains("Exp "), "Expiry should not be in output when empty");
    }

    @Test
    void rendersValidCode128Barcode() throws PrinterDriver.PrinterException {
        PrintLabelRequest req = new PrintLabelRequest(
            "CODE-12345",
            "Test Product",
            "Test",
            "100",
            "20000",
            "LOT-001",
            null,
            "2026-07-27"
        );

        String tspl = service.renderLabel(req);

        assertTrue(tspl.contains("BARCODE"), "A barcode command must be present");
        assertTrue(tspl.contains("\"128\""), "Must be a Code 128 barcode");
        assertTrue(tspl.contains("CODE-12345"), "Barcode value must be in output");
    }

    @Test
    void drawsTheLabelInBothColumnsSoNoStickerFeedsOutBlank() throws PrinterDriver.PrinterException {
        PrintLabelRequest req = new PrintLabelRequest(
            "PROD-005", "Kettle", "Kitchen", "100", "20000", "LOT-3", null, "2026-07-27");

        String tspl = service.renderLabel(req);

        // One document is one row of the 2-up stock: the same barcode drawn twice, once per column.
        int first = tspl.indexOf("BARCODE");
        int second = tspl.indexOf("BARCODE", first + 1);
        assertTrue(first >= 0 && second > first, "expected the barcode in both columns");
        assertEquals(2, countOf(tspl, "PROD-005"), "the code appears once per column");
    }

    @Test
    void rowsForRoundsUpSoAnOddAskOverDeliversRatherThanFeedingABlank() {
        assertEquals(1, LabelTemplateService.rowsFor(1));
        assertEquals(1, LabelTemplateService.rowsFor(2));
        assertEquals(2, LabelTemplateService.rowsFor(3));
        assertEquals(3, LabelTemplateService.rowsFor(6));
        assertEquals(1, LabelTemplateService.rowsFor(0), "a degenerate ask still prints one row");
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) != -1) {
            count++;
            from += needle.length();
        }
        return count;
    }

    @Test
    void neverEmitsNonAsciiCharacters() throws PrinterDriver.PrinterException {
        // The driver sends bytes as US-ASCII; a non-ASCII character (the rupee sign, a Unicode
        // ellipsis) would silently become "?" rather than throw, so the template must not emit one
        // — including on the truncation path, which a long name forces.
        PrintLabelRequest req = new PrintLabelRequest(
            "PROD-003",
            "A Genuinely Very Long Product Name That Will Need To Be Truncated For This Tiny Label",
            "Kitchen",
            "999",
            "199900",
            "LOT-1",
            null,
            "2026-07-27"
        );

        String tspl = service.renderLabel(req);

        for (int i = 0; i < tspl.length(); i++) {
            char c = tspl.charAt(i);
            assertTrue(c < 128,
                "non-ASCII character in rendered label: '" + c + "' (U+" + Integer.toHexString(c) + ")");
        }
    }

    @Test
    void aQuoteInAFieldDoesNotBreakTheCommandSyntax() throws PrinterDriver.PrinterException {
        PrintLabelRequest req = new PrintLabelRequest(
            "PROD-004",
            "12\" Cable",
            "Wireless",
            "50",
            "9900",
            "LOT-2",
            null,
            "2026-07-27"
        );

        String tspl = service.renderLabel(req);

        assertFalse(tspl.contains("12\" Cable"), "a literal quote must not reach the command text");
        assertTrue(tspl.contains("12' Cable"), "the quote is replaced, not dropped");
    }
}
