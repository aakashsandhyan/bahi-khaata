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

        String zpl = service.renderLabel(req);

        assertTrue(zpl.contains("^XA"), "ZPL must start with ^XA");
        assertTrue(zpl.contains("^XZ"), "ZPL must end with ^XZ");
        assertTrue(zpl.contains("PROD-001"), "Barcode must be in output");
        assertTrue(zpl.contains("Coconut Oil 1L"), "Product name must be in output");
        assertTrue(zpl.contains("Kitchen"), "Category must be in output");
        assertTrue(zpl.contains("240"), "Cost must be in output");
        assertTrue(zpl.contains("45000"), "MRP must be in output");
        assertTrue(zpl.contains("LOT-2024-07"), "Lot must be in output");
        assertTrue(zpl.contains("2027-07-27"), "Expiry must be in output");
        assertTrue(zpl.contains("2026-07-27"), "Received date must be in output");
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

        String zpl = service.renderLabel(req);

        assertTrue(zpl.contains("^XA"), "ZPL must start with ^XA");
        assertTrue(zpl.contains("Face Cream"), "Product name must be in output");
        assertFalse(zpl.contains("Exp: "), "Expiry should not be in output when empty");
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

        String zpl = service.renderLabel(req);

        assertTrue(zpl.contains("^BC"), "Code128 barcode command must be present");
        assertTrue(zpl.contains("CODE-12345"), "Barcode value must be in output");
    }
}
