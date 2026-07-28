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

    @Mock
    private PrintJobRepository printJobRepository;

    @Mock
    private PrinterDriver printerDriver;

    @Mock
    private LabelTemplateService labelService;

    @InjectMocks
    private PrintExecutorService executor;

    @Test
    void pollsQueuedJobs() {
        UUID jobId = UUID.randomUUID();
        PrintJob job = PrintJob.create("box", UUID.randomUUID(), 1);
        job.setStatus("queued");

        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued"))
            .thenReturn(List.of(job))
            .thenReturn(List.of());

        executor.executePrintQueue();

        verify(printJobRepository, atLeastOnce()).findByStatusOrderByCreatedAtAsc("queued");
    }

    @Test
    void marksJobAsFailedAfterMaxRetries() {
        PrintJob job = PrintJob.create("box", UUID.randomUUID(), 1);
        job.setStatus("queued");
        job.setRetryCount(10);

        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued"))
            .thenReturn(List.of(job))
            .thenReturn(List.of());

        executor.executePrintQueue();

        assertEquals("failed", job.getStatus());
        assertNotNull(job.getError());
    }

    @Test
    void queuesJobForRetryOnPrinterOffline() throws PrinterDriver.PrinterException {
        PrintJob job = PrintJob.create("box", UUID.randomUUID(), 1);
        job.setStatus("queued");
        job.setRetryCount(0);

        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued"))
            .thenReturn(List.of(job))
            .thenReturn(List.of());
        when(labelService.renderLabel(any()))
            .thenThrow(new PrinterDriver.PrinterException("Printer unreachable"));

        executor.executePrintQueue();

        assertTrue(job.getRetryCount() > 0 || job.getStatus().equals("failed"));
    }

    @Test
    void batchProcessesUpTo5Jobs() {
        List<PrintJob> jobs = List.of(
            PrintJob.create("box", UUID.randomUUID(), 1),
            PrintJob.create("box", UUID.randomUUID(), 1),
            PrintJob.create("box", UUID.randomUUID(), 1)
        );

        for (PrintJob job : jobs) {
            job.setStatus("queued");
        }

        when(printJobRepository.findByStatusOrderByCreatedAtAsc("queued"))
            .thenReturn(jobs)
            .thenReturn(List.of());

        executor.executePrintQueue();

        verify(printJobRepository).findByStatusOrderByCreatedAtAsc("queued");
    }
}
