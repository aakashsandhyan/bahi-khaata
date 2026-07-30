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

import com.bahikhaata.backend.inventory.LotReconciliation;
import com.bahikhaata.contracts.LotPhantomReport;
import com.bahikhaata.contracts.WriteOffResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preview and apply a lot's phantom-stock write-off from the pricing workbench. */
@RestController
@RequestMapping("/api/pricing/lots/{lotId}")
public class LotReconciliationController {

    private final LotReconciliation reconciliation;

    public LotReconciliationController(LotReconciliation reconciliation) {
        this.reconciliation = reconciliation;
    }

    @GetMapping("/reconcile")
    public LotPhantomReport preview(@PathVariable UUID lotId) {
        return reconciliation.phantomReport(lotId);
    }

    @PostMapping("/write-off")
    public WriteOffResult writeOff(@PathVariable UUID lotId) {
        return reconciliation.writeOff(lotId, Instant.now());
    }
}
