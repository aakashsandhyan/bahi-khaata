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

/**
 * Sends a bill to the ESC/POS receipt printer — a second physical printer, separate from the label
 * {@link PrinterDriver} (ESC/POS bytes, not TSPL). Reuses the label driver's exception and status
 * types so callers handle both printers the same way.
 */
public interface ReceiptPrinterDriver {

    /** Sends an already-rendered ESC/POS byte stream to the receipt printer. */
    void printReceipt(byte[] escpos) throws PrinterDriver.PrinterException;

    /** Prints a short test slip and reports whether the printer answered. Never throws. */
    PrinterDriver.PrinterStatus test();
}
