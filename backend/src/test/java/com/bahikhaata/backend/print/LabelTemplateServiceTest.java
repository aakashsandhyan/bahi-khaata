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

/**
 * The label body is composed server-side into a bitmap (the firmware's fonts lie about their
 * widths), so these tests check the TSPL structure and the barcode — the parts still readable in
 * the command stream. What the bitmap looks like is the in-person test print's job.
 */
class LabelTemplateServiceTest {

    private final LabelTemplateService service = new LabelTemplateService();

    private static final PrintLabelRequest WITH_MRP =
            new PrintLabelRequest("BBZ-100042", "Prestige Cooker 5L", 1499_00L, 449_00L);
    private static final PrintLabelRequest WITHOUT_MRP =
            new PrintLabelRequest("BBZ-100043", "Milton Flask 1000ml", null, 299_00L);

    @Test
    void rendersOneRowWithBitmapBodyAndNativeBarcode() {
        String tspl = service.renderLabel(WITH_MRP);

        assertTrue(tspl.contains("SIZE 82mm,25mm"), "declares the measured 2-up web");
        assertTrue(tspl.contains("GAP 3mm,0mm"), "declares the measured row gap");
        assertTrue(tspl.contains("CLS"), "clears the buffer before drawing");
        assertTrue(tspl.contains("PRINT 1,1"), "fires the print at the end");
        assertEquals(2, countOf(tspl, "BITMAP "), "the composed body prints once per column");
        assertEquals(0, countOf(tspl, "BARCODE "),
            "no native barcode — the bars are encoded in-process and live in the bitmap");
    }

    @Test
    void aRowCanCarryTwoDifferentLabels() {
        String tspl = service.renderRow(WITH_MRP, WITHOUT_MRP);

        assertEquals(2, countOf(tspl, "BITMAP "), "one composed image per column");
        // The two columns carry different content, so their bitmap payloads must differ.
        int first = tspl.indexOf("BITMAP ");
        int second = tspl.indexOf("BITMAP ", first + 1);
        assertNotEquals(tspl.substring(first, second), tspl.substring(second), "columns differ");
    }

    @Test
    void theWholeStreamStaysSingleByte() {
        // The driver sends ISO-8859-1; every char must fit one byte, including the bitmap data.
        String tspl = service.renderRow(WITH_MRP, WITHOUT_MRP);
        for (int i = 0; i < tspl.length(); i++) {
            assertTrue(tspl.charAt(i) < 256, "char beyond one byte at " + i);
        }
    }

    @Test
    void rowsForRoundsUpSoAnOddAskOverDeliversRatherThanFeedingABlank() {
        assertEquals(1, LabelTemplateService.rowsFor(1));
        assertEquals(1, LabelTemplateService.rowsFor(2));
        assertEquals(2, LabelTemplateService.rowsFor(3));
        assertEquals(3, LabelTemplateService.rowsFor(6));
        assertEquals(1, LabelTemplateService.rowsFor(0), "a degenerate ask still prints one row");
    }

    @Test
    void anyBarcodeTextEncodesWithoutBreakingTheStream() {
        // The bars are pixels now; even a quote or an odd character cannot break a command.
        PrintLabelRequest quoted = new PrintLabelRequest("BB\"Z-99", "Thing", null, 99_00L);
        String tspl = service.renderLabel(quoted);
        assertTrue(tspl.contains("PRINT 1,1"), "renders cleanly whatever the code contains");
    }

    @Test
    void aNullNameStillRenders() {
        PrintLabelRequest nameless = new PrintLabelRequest("BBZ-5", null, null, 99_00L);
        String tspl = service.renderLabel(nameless);
        assertTrue(tspl.contains("PRINT 1,1"), "renders without a name rather than throwing");
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
}
