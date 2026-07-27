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
 * Renders ZPL label templates for barcode printing.
 *
 * <p>Substitutes product data into a ZPL template,
 * producing printer-ready commands for TSC TE-244.
 */
@Service
public class LabelTemplateService {

    public String renderLabel(PrintLabelRequest request) throws PrinterDriver.PrinterException {
        return buildZpl(request);
    }

    private String buildZpl(PrintLabelRequest req) {
        StringBuilder zpl = new StringBuilder();
        zpl.append("^XA\n");
        zpl.append("^FO50,50^BY2,3.0^BC^FD").append(req.barcode()).append("^FS\n");
        zpl.append("^FO50,150^A0N,28,28^FD").append(req.productName()).append("^FS\n");
        zpl.append("^FO50,190^A0N,14,14^FD").append(req.category()).append("^FS\n");
        zpl.append("^FO50,280^A0N,18,18^FDCost: ₹").append(req.costPerUnit()).append("/u^FS\n");
        zpl.append("^FO50,310^A0N,18,18^FDMRP: ₹").append(req.mrpPaise()).append("^FS\n");
        zpl.append("^FO500,280^A0N,12,12^FDLot: ").append(req.lotId()).append("^FS\n");

        if (req.expiryDate() != null && !req.expiryDate().isEmpty()) {
            zpl.append("^FO500,310^A0N,12,12^FDExp: ").append(req.expiryDate()).append("^FS\n");
        }

        zpl.append("^FO50,400^A0N,12,12^FDRec: ").append(req.receivedDate()).append("^FS\n");
        zpl.append("^XZ\n");

        return zpl.toString();
    }
}
