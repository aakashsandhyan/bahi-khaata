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

    private static final PrintLabelRequest WITH_MRP =
            new PrintLabelRequest("BBZ-100042", "Prestige Cooker 5L", 1499_00L, 449_00L);
    private static final PrintLabelRequest WITHOUT_MRP =
            new PrintLabelRequest("BBZ-100043", "Milton Flask 1000ml", null, 299_00L);

    @Test
    void rendersTheOneLabelWithMrpStruckAndSaving() {
        String tspl = service.renderLabel(WITH_MRP);

        assertTrue(tspl.contains("SIZE 82mm,24mm"), "declares the measured 2-up web");
        assertTrue(tspl.contains("CLS"), "clears the buffer before drawing");
        assertTrue(tspl.contains("PRINT 1,1"), "fires the print at the end");
        assertTrue(tspl.contains("BITMAP"), "the Devanagari wordmark ships as a bitmap");
        assertTrue(tspl.contains("\"128\""), "Code 128 barcode");
        assertTrue(tspl.contains("BBZ-100042"), "barcode value present");
        assertTrue(tspl.contains("Prestige Cooker 5L"), "name present");
        assertTrue(tspl.contains("MRP Rs.1499"), "confirmed MRP printed");
        assertTrue(tspl.contains("BAR "), "the MRP is struck through with a BAR");
        assertTrue(tspl.contains("70% OFF"), "saving derived from the two figures");
        assertTrue(tspl.contains("Rs.449"), "the shop's price is the hero");
    }

    @Test
    void withoutMrpTheLabelClaimsNothing() {
        String tspl = service.renderLabel(WITHOUT_MRP);

        assertTrue(tspl.contains("Rs.299"), "price prints alone");
        assertFalse(tspl.contains("MRP"), "no MRP line when none is confirmed");
        assertFalse(tspl.contains("% OFF"), "no saving claimed without an MRP");
        assertFalse(tspl.contains("BAR "), "nothing to strike through");
    }

    @Test
    void anMrpNotAboveThePriceIsNotClaimed() {
        // A degenerate figure (MRP at or below our price) would advertise a 0% saving or worse —
        // print the price alone instead.
        PrintLabelRequest odd = new PrintLabelRequest("BBZ-1", "Thing", 200_00L, 250_00L);
        String tspl = service.renderLabel(odd);

        assertFalse(tspl.contains("MRP"), "an MRP below the price does not print");
        assertTrue(tspl.contains("Rs.250"));
    }

    @Test
    void aRowCanCarryTwoDifferentLabels() {
        String tspl = service.renderRow(WITH_MRP, WITHOUT_MRP);

        assertTrue(tspl.contains("BBZ-100042") && tspl.contains("BBZ-100043"),
                "each column carries its own product");
        assertEquals(2, countOf(tspl, "BITMAP"), "the wordmark prints once per column");
        // Only the left label has an MRP: exactly one strike bar, one saving.
        assertEquals(1, countOf(tspl, "BAR "), "one strike for the one MRP");
        assertEquals(1, countOf(tspl, "% OFF"));
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
    void textCommandsStayAsciiAndTheWholeStreamStaysSingleByte() {
        // The driver sends ISO-8859-1: every char must fit one byte (the BITMAP data uses the full
        // range), and the TEXT lines must stay pure ASCII — the printer's fonts have nothing else.
        PrintLabelRequest longName = new PrintLabelRequest(
                "BBZ-9", "A Genuinely Very Long Product Name That Must Truncate", 999_00L, 99_00L);
        String tspl = service.renderLabel(longName);

        for (int i = 0; i < tspl.length(); i++) {
            assertTrue(tspl.charAt(i) < 256, "char beyond one byte at " + i);
        }
        for (String line : tspl.split("\r\n")) {
            if (line.startsWith("TEXT")) {
                for (int i = 0; i < line.length(); i++) {
                    assertTrue(line.charAt(i) < 128,
                            "non-ASCII in a TEXT line: " + line);
                }
            }
        }
    }

    @Test
    void aQuoteInAFieldDoesNotBreakTheCommandSyntax() {
        PrintLabelRequest quoted = new PrintLabelRequest("BBZ-2", "12\" Cable", null, 99_00L);
        String tspl = service.renderLabel(quoted);

        assertFalse(tspl.contains("12\" Cable"), "a literal quote must not reach the command text");
        assertTrue(tspl.contains("12' Cable"), "the quote is replaced, not dropped");
    }

    @Test
    void paiseRenderOnlyWhenTheyExist() {
        PrintLabelRequest odd = new PrintLabelRequest("BBZ-3", "Thing", null, 249_50L);
        assertTrue(service.renderLabel(odd).contains("Rs.249.50"), "real paise show two decimals");

        PrintLabelRequest whole = new PrintLabelRequest("BBZ-4", "Thing", null, 249_00L);
        assertTrue(service.renderLabel(whole).contains("Rs.249"), "whole rupees stay whole");
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
