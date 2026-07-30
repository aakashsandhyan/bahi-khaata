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
package com.bahikhaata.backend.pricing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bahikhaata.contracts.ApproveCaptureRequest;
import com.bahikhaata.contracts.CaptureRequest;
import com.bahikhaata.contracts.CaptureSummary;
import com.bahikhaata.contracts.PriceManualRequest;
import com.bahikhaata.contracts.ShelfPricedProduct;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaptureServiceTest {

    @Mock private ProductCaptureRepository captures;
    @Mock private ShelfPricing shelfPricing;

    private CaptureService service() {
        return new CaptureService(captures, shelfPricing);
    }

    @Test
    void captureCarriesNoPriceAndStartsPending() {
        when(captures.save(any())).thenAnswer(i -> i.getArgument(0));

        CaptureSummary summary = service().capture(
                new CaptureRequest("Mystery kettle", 149900L, "steel", null));

        assertEquals("Mystery kettle", summary.name());
        assertEquals(ProductCapture.PENDING, summary.status());
        // A capture never touches the shelf on its own.
        verifyNoInteractions(shelfPricing);
    }

    @Test
    void aCaptureNeedsAName() {
        assertThrows(IllegalArgumentException.class,
                () -> service().capture(new CaptureRequest("  ", null, null, null)));
    }

    @Test
    void approvingRunsTheManualSaveAndMarksApproved() {
        UUID captureId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        ProductCapture capture = new ProductCapture("Mystery kettle", 149900L, null, null);
        when(captures.findById(captureId)).thenReturn(Optional.of(capture));
        when(shelfPricing.saveManual(any())).thenReturn(
                new ShelfPricedProduct(UUID.randomUUID(), "BBZ-1", "Mystery kettle", 49900L, 149900L));

        service().approve(captureId, new ApproveCaptureRequest(
                lotId, "KITCHEN", "GOOD", 3L, 49900L, null));

        // The captured MRP is carried through when the reviewer gives none.
        ArgumentCaptor<PriceManualRequest> req = ArgumentCaptor.forClass(PriceManualRequest.class);
        verify(shelfPricing).saveManual(req.capture());
        assertEquals("Mystery kettle", req.getValue().name());
        assertEquals(149900L, req.getValue().mrpPaise());
        assertEquals(lotId, req.getValue().lotId());
        assertEquals(ProductCapture.APPROVED, capture.getStatus());
    }

    @Test
    void rejectingCreatesNoProduct() {
        UUID captureId = UUID.randomUUID();
        ProductCapture capture = new ProductCapture("Junk", null, null, null);
        when(captures.findById(captureId)).thenReturn(Optional.of(capture));

        service().reject(captureId);

        assertEquals(ProductCapture.REJECTED, capture.getStatus());
        verifyNoInteractions(shelfPricing);
    }
}
