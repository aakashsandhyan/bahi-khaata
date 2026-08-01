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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bahikhaata.contracts.PaymentMethod;
import com.bahikhaata.contracts.SaleLineView;
import com.bahikhaata.contracts.SaleView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiptTemplateServiceTest {

    @Mock private BillSettingsRepository settings;

    private BillSettings compositionSettings() {
        BillSettings s = new BillSettings();
        s.setShopName("Bachat Baazar");
        s.setAddress("Shop 12, MP Nagar, Bhopal");
        s.setGstin("23ABCDE1234F1Z5");
        s.setBillTitle("Bill of Supply");
        s.setDeclaration("Composition taxable person, not eligible to collect tax on supplies");
        s.setFooter("Thank you! Visit again");
        return s;
    }

    private SaleView sampleSale() {
        List<SaleLineView> lines = List.of(
                new SaleLineView(UUID.randomUUID(),
                        "Pigeon Inox Rice Cooker 1L Stainless Steel", "BBZ-100035",
                        149_500, 99_900, 1, 99_900, 49_600),
                new SaleLineView(UUID.randomUUID(),
                        "Steel Kadai with Lid", "BBZ-100002",
                        99_900, 49_900, 2, 99_800, 100_000));
        return new SaleView(
                UUID.randomUUID(), 42, "BB-000042", PaymentMethod.CASH,
                199_700, 149_600, 0, 199_700, "Ravi",
                Instant.parse("2026-08-02T09:00:00Z"), lines, false);
    }

    @Test
    void rendersACompositionBillOfSupply() {
        when(settings.findById(BillSettings.SINGLETON_ID))
                .thenReturn(Optional.of(compositionSettings()));
        ReceiptTemplateService template = new ReceiptTemplateService(settings);

        String bill = template.renderText(sampleSale());
        System.out.println(
                "\n===== SAMPLE BILL (80mm, 48 cols) =====\n" + bill + "=======================================");

        // Header, identity, and the composition wording — the legally required bits.
        assertThat(bill)
                .contains("Bachat Baazar")
                .contains("GSTIN: 23ABCDE1234F1Z5")
                .contains("Bill of Supply")
                .contains("BB-000042")
                .contains("02-Aug-2026") // in IST
                .contains("Cashier: Ravi")
                .contains("Steel Kadai with Lid")
                .contains("2 x 499.00")
                .contains("998.00")
                .contains("You saved")
                .contains("TOTAL")
                .contains("1,997.00")
                .contains("Paid: CASH")
                // The declaration word-wraps across two 48-col lines, so check a token that fits one.
                .contains("Composition taxable person");

        // A composition bill collects and shows no tax.
        assertThat(bill).doesNotContain("CGST", "SGST", "Tax Invoice");

        // And the bytes carry the ESC/POS reset so the printer starts clean.
        byte[] bytes = template.render(sampleSale());
        assertThat(bytes).startsWith(new byte[] {0x1B, 0x40});
    }
}
