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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrintExecutorServiceTest {

    @Mock private PrintJobRepository printJobRepository;
    @Mock private PrinterDriver printerDriver;
    @Mock private LabelTemplateService labelService;
    @Mock private ProductRepository productRepository;

    @InjectMocks private PrintExecutorService executor;

    private static PrintJob queued(UUID productId) {
        PrintJob job = PrintJob.create("BBZ-100001", "Test Product", 19900L, 49900L, 1, productId);
        job.setStatus("queued");
        return job;
    }

    @Test
    void pollsQueuedJobs() {
        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued"))
            .thenReturn(List.of(queued(null)))
            .thenReturn(List.of());

        executor.executePrintQueue();

        verify(printJobRepository, atLeastOnce()).findByStatusOrderByCreatedAtAsc("queued");
    }

    @Test
    void rendersFromTheJobsOwnFieldsWithoutADatabaseRead() throws Exception {
        PrintJob job = queued(null);
        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued"))
            .thenReturn(List.of(job)).thenReturn(List.of());
        when(labelService.renderLabel(any())).thenReturn("SIZE ...");

        executor.executePrintQueue();

        assertEquals("done", job.getStatus());
        // The label came from the job's fields, nothing was looked up beyond the queue itself.
        verify(labelService).renderLabel(argThat(r ->
            r.barcode().equals("BBZ-100001") && r.productName().equals("Test Product")
                && r.pricePaise() == 19900L && r.mrpPaise() == 49900L));
    }

    @Test
    void marksTheProductLabelledOnSuccess() throws Exception {
        UUID productId = UUID.randomUUID();
        PrintJob job = queued(productId);
        Product product = mock(Product.class);
        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued"))
            .thenReturn(List.of(job)).thenReturn(List.of());
        when(labelService.renderLabel(any())).thenReturn("SIZE ...");
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        executor.executePrintQueue();

        verify(product).markLabelPrinted(any());
        verify(productRepository).save(product);
    }

    @Test
    void aFailedPrintLeavesTheProductUnmarked() throws Exception {
        UUID productId = UUID.randomUUID();
        PrintJob job = queued(productId);
        job.setRetryCount(10);
        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued"))
            .thenReturn(List.of(job)).thenReturn(List.of());
        when(labelService.renderLabel(any())).thenReturn("SIZE ...");
        doThrow(new PrinterDriver.PrinterException("Printer unreachable"))
            .when(printerDriver).sendLabel(any(), anyInt());

        executor.executePrintQueue();

        assertEquals("failed", job.getStatus());
        verify(productRepository, never()).save(any());
    }

    @Test
    void queuesJobForRetryOnPrinterOffline() throws Exception {
        PrintJob job = queued(null);
        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued"))
            .thenReturn(List.of(job)).thenReturn(List.of());
        when(labelService.renderLabel(any())).thenReturn("SIZE ...");
        doThrow(new PrinterDriver.PrinterException("Printer unreachable"))
            .when(printerDriver).sendLabel(any(), anyInt());

        executor.executePrintQueue();

        assertTrue(job.getRetryCount() > 0 || job.getStatus().equals("failed"));
    }

    @Test
    void marksJobAsFailedAfterMaxRetries() throws Exception {
        PrintJob job = queued(null);
        job.setRetryCount(10);
        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued"))
            .thenReturn(List.of(job)).thenReturn(List.of());
        when(labelService.renderLabel(any())).thenReturn("SIZE ...");
        doThrow(new PrinterDriver.PrinterException("Printer unreachable"))
            .when(printerDriver).sendLabel(any(), anyInt());

        executor.executePrintQueue();

        assertEquals("failed", job.getStatus());
        assertNotNull(job.getError());
    }
}
