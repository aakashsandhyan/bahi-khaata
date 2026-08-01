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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.contracts.BulkPrintResult;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.Money;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BulkLabelPrintTest {

    @Mock private ProductRepository products;
    @Mock private BarcodeRepository barcodes;
    @Mock private com.bahikhaata.backend.catalog.BarcodeResolver barcodeResolver;
    @Mock private BatchRepository batches;
    @Mock private LabelTemplateService labelService;
    @Mock private PrinterDriver printerDriver;

    private BulkLabelPrint bulk() {
        return new BulkLabelPrint(
                products, barcodes, barcodeResolver, batches, labelService, printerDriver);
    }

    private Product priced(String name) {
        Product p = new Product(name, Category.of("KITCHEN"), java.util.Map.of());
        p.setSellingPrice(Money.ofRupees(100));
        return p;
    }

    @Test
    void threeProductsPrintAsTwoRowsAndAllAreMarked() throws Exception {
        Product a = priced("A");
        Product b = priced("B");
        Product c = priced("C");
        when(products.findById(a.getId())).thenReturn(Optional.of(a));
        when(products.findById(b.getId())).thenReturn(Optional.of(b));
        when(products.findById(c.getId())).thenReturn(Optional.of(c));
        when(barcodes.findByProductId(any())).thenReturn(List.of());
        when(batches.findByProductIdNewestFirst(any())).thenReturn(List.of());
        when(labelService.renderRow(any(), any())).thenReturn("SIZE ...");

        BulkPrintResult result = bulk().printBulk(List.of(a.getId(), b.getId(), c.getId()));

        assertThat(result.printed()).isEqualTo(3);
        assertThat(result.failed()).isZero();
        // ceil(3/2) = 2 rows sent; the odd one prints as a duplicate pair.
        verify(printerDriver, times(2)).sendLabel(any(), anyInt());
        // All three marked label-printed.
        assertThat(a.isLabelPrinted()).isTrue();
        assertThat(b.isLabelPrinted()).isTrue();
        assertThat(c.isLabelPrinted()).isTrue();
    }

    @Test
    void aFailedRowLeavesItsProductsUnmarked() throws Exception {
        Product a = priced("A");
        when(products.findById(a.getId())).thenReturn(Optional.of(a));
        when(barcodes.findByProductId(any())).thenReturn(List.of());
        when(batches.findByProductIdNewestFirst(any())).thenReturn(List.of());
        when(labelService.renderRow(any(), any())).thenReturn("SIZE ...");
        doThrow(new PrinterDriver.PrinterException("Printer unreachable"))
                .when(printerDriver).sendLabel(any(), anyInt());

        BulkPrintResult result = bulk().printBulk(List.of(a.getId()));

        assertThat(result.printed()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(a.isLabelPrinted()).isFalse();
    }

    @Test
    void unpricedOrMissingProductsAreCountedAsFailedAndNotPrinted() {
        UUID missing = UUID.randomUUID();
        when(products.findById(missing)).thenReturn(Optional.empty());

        BulkPrintResult result = bulk().printBulk(List.of(missing));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.printed()).isZero();
        verifyNoInteractions(printerDriver);
    }

    @Test
    void reprintLookupReturnsTheCurrentLabelFigures() {
        Product p = priced("Cooker");
        com.bahikhaata.backend.catalog.Barcode bbz =
                mock(com.bahikhaata.backend.catalog.Barcode.class);
        when(bbz.getOrigin()).thenReturn(com.bahikhaata.contracts.Origin.INTERNAL);
        when(bbz.getCode()).thenReturn("BBZ-100042");
        when(barcodeResolver.resolve("B08RWJ5MGW")).thenReturn(Optional.of(p));
        when(barcodes.findByProductId(p.getId())).thenReturn(List.of(bbz));
        var batch = mock(com.bahikhaata.backend.inventory.Batch.class);
        when(batch.getMrp()).thenReturn(Money.ofRupees(400));
        when(batch.isMrpEstimate()).thenReturn(false);
        when(batches.findByProductIdNewestFirst(p.getId())).thenReturn(List.of(batch));

        var found = bulk().labelByBarcode("B08RWJ5MGW");

        // The label carries the BBZ, the current name/price, and the confirmed MRP.
        assertThat(found.barcode()).isEqualTo("BBZ-100042");
        assertThat(found.name()).isEqualTo("Cooker");
        assertThat(found.sellingPricePaise()).isEqualTo(10_000L);
        assertThat(found.mrpPaise()).isEqualTo(40_000L);
    }

    @Test
    void reprintLookupRefusesAnUnknownBarcode() {
        when(barcodeResolver.resolve("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bulk().labelByBarcode("NOPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No product");
    }

    @Test
    void reprintLookupRefusesAnUnpricedProduct() {
        Product p = new Product("Unpriced", Category.of("KITCHEN"), java.util.Map.of());
        when(barcodeResolver.resolve("BBZ-1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> bulk().labelByBarcode("BBZ-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not priced");
    }
}
