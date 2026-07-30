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

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrintExecutorServiceTest {

    @Mock private PrintJobRepository printJobRepository;
    @Mock private PrinterDriver printerDriver;
    @Mock private LabelTemplateService labelService;
    @Mock private ProductRepository productRepository;

    @InjectMocks private PrintExecutorService executor;

    private static PrintJob queued(UUID productId) {
        PrintJob job = PrintJob.create("BBZ-1", "Test Product", 49900L, 149900L, 1, productId);
        job.setStatus("queued");
        return job;
    }

    @Test
    void aLoneLabelIsHeldNotPrinted() throws Exception {
        PrintJob lone = queued(null);
        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued")).thenReturn(List.of(lone));

        executor.executePrintQueue();

        // One label alone cannot fill a 2-up row without waste, so it waits.
        verify(printerDriver, never()).sendLabel(any(), anyInt());
        assertEquals("queued", lone.getStatus());
    }

    @Test
    void twoLabelsPrintAsOneRowAndBothAreMarked() throws Exception {
        UUID pa = UUID.randomUUID();
        UUID pb = UUID.randomUUID();
        PrintJob a = queued(pa);
        PrintJob b = queued(pb);
        Product prodA = mock(Product.class);
        Product prodB = mock(Product.class);
        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued")).thenReturn(List.of(a, b));
        when(labelService.renderRow(any(), any())).thenReturn("SIZE ...");
        when(productRepository.findById(pa)).thenReturn(Optional.of(prodA));
        when(productRepository.findById(pb)).thenReturn(Optional.of(prodB));

        executor.executePrintQueue();

        // Both go out on one row, both done, both products marked.
        verify(printerDriver, times(1)).sendLabel(any(), anyInt());
        assertEquals("done", a.getStatus());
        assertEquals("done", b.getStatus());
        verify(prodA).markLabelPrinted(any());
        verify(prodB).markLabelPrinted(any());
    }

    @Test
    void oddQueuePrintsOnePairAndHoldsTheLeftover() throws Exception {
        PrintJob a = queued(null);
        PrintJob b = queued(null);
        PrintJob c = queued(null);
        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued")).thenReturn(List.of(a, b, c));
        when(labelService.renderRow(any(), any())).thenReturn("SIZE ...");

        executor.executePrintQueue();

        verify(printerDriver, times(1)).sendLabel(any(), anyInt()); // one pair
        assertEquals("done", a.getStatus());
        assertEquals("done", b.getStatus());
        assertEquals("queued", c.getStatus()); // held
    }

    @Test
    void flushPrintsALoneLeftoverAsADuplicatePair() throws Exception {
        UUID p = UUID.randomUUID();
        PrintJob lone = queued(p);
        Product product = mock(Product.class);
        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued")).thenReturn(List.of(lone));
        when(labelService.renderLabel(any())).thenReturn("SIZE ...");
        when(productRepository.findById(p)).thenReturn(Optional.of(product));

        long printed = executor.flushPending();

        assertEquals(1, printed);
        verify(labelService).renderLabel(any()); // duplicate pair of the one product
        assertEquals("done", lone.getStatus());
        verify(product).markLabelPrinted(any());
    }

    @Test
    void offlinePrinterRequeuesTheRowForRetry() throws Exception {
        PrintJob a = queued(null);
        PrintJob b = queued(null);
        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued")).thenReturn(List.of(a, b));
        when(labelService.renderRow(any(), any())).thenReturn("SIZE ...");
        doThrow(new PrinterDriver.PrinterException("Printer unreachable"))
            .when(printerDriver).sendLabel(any(), anyInt());

        executor.executePrintQueue();

        // Both go back to queued to retry, with the retry count bumped.
        assertEquals("queued", a.getStatus());
        assertEquals("queued", b.getStatus());
        assertTrue(a.getRetryCount() > 0);
    }

    @Test
    void pendingCountIsTheNumberOfHeldLabels() {
        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued"))
            .thenReturn(List.of(queued(null), queued(null)));

        assertEquals(2, executor.pendingCount());
    }
}
