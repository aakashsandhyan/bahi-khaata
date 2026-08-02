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

import com.bahikhaata.contracts.SaleView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Renders a sale and sends its bill to the receipt printer. Printing is best-effort and always
 * called <em>after</em> the sale has committed: a failure is reported so the operator can reprint,
 * never propagated, so a jammed or offline printer can never undo a recorded sale.
 */
@Service
public class ReceiptPrinting {

    private static final Logger log = LoggerFactory.getLogger(ReceiptPrinting.class);

    private final ReceiptPrinterDriver driver;
    private final ReceiptTemplateService template;

    ReceiptPrinting(ReceiptPrinterDriver driver, ReceiptTemplateService template) {
        this.driver = driver;
        this.template = template;
    }

    /** Prints the bill for a sale. Returns true if printing failed — the sale stands regardless. */
    public boolean printBill(SaleView sale) {
        try {
            driver.printReceipt(template.render(sale));
            return false;
        } catch (PrinterDriver.PrinterException e) {
            log.warn("Bill {} did not print: {}", sale.billNoFormatted(), e.getMessage());
            return true;
        }
    }

    /** A test print, for the admin config screen. */
    public PrinterDriver.PrinterStatus test() {
        return driver.test();
    }
}
