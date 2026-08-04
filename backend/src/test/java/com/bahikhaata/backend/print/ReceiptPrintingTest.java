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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bahikhaata.contracts.PaymentMethod;
import com.bahikhaata.contracts.SaleView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Printing is best-effort: a jam or an offline printer is flagged, never thrown at the sale. */
@ExtendWith(MockitoExtension.class)
class ReceiptPrintingTest {

    @Mock private ReceiptPrinterDriver driver;
    @Mock private ReceiptTemplateService template;

    private SaleView sale() {
        return new SaleView(
                UUID.randomUUID(), 42, "BB-000042", PaymentMethod.CASH,
                99_900, 50_000, 0, 99_900, "Ravi", Instant.parse("2026-08-02T09:00:00Z"),
                List.of(), false);
    }

    @Test
    void aSuccessfulPrintReportsNoFailure() throws Exception {
        when(template.render(any())).thenReturn(new byte[] {0x1B, 0x40});
        ReceiptPrinting printing = new ReceiptPrinting(driver, template);

        assertThat(printing.printBill(sale())).isFalse();
        verify(driver).printReceipt(any());
    }

    @Test
    void aPrinterFailureIsFlaggedNeverThrown() throws Exception {
        when(template.render(any())).thenReturn(new byte[] {0x1B, 0x40});
        doThrow(new PrinterDriver.PrinterException("printer offline"))
                .when(driver).printReceipt(any());
        ReceiptPrinting printing = new ReceiptPrinting(driver, template);

        // The sale has already committed by the time this runs; a print failure must come back as a
        // flag, not an exception, so nothing rolls back — the operator reprints from the stored sale.
        assertThat(printing.printBill(sale())).isTrue();
    }
}
