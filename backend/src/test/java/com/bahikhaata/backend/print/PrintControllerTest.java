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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bahikhaata.contracts.QueuePrintJobRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrintControllerTest {

    @Mock private PrintJobRepository jobs;
    @Mock private PrintExecutorService executor;
    @Mock private BulkLabelPrint labels;

    @Test
    void queueingCopiesCreatesThatManySingleLabelJobs() {
        when(jobs.save(any())).thenAnswer(i -> i.getArgument(0));
        PrintController controller = new PrintController(jobs, executor, labels);

        var response = controller.queuePrintJob(new QueuePrintJobRequest(
                "BBZ-100042", "Cooker", 51_000L, null, 3, UUID.randomUUID()));

        // Three labels means three jobs of one, not one job of three — the hold-and-pair executor
        // pairs jobs and ignores a job's copies count, so N jobs are what become N labels.
        verify(jobs, times(3)).save(argThat(job -> job.getCopies() == 1));
        assertThat(response.getBody().copies()).isEqualTo(3);
    }
}
