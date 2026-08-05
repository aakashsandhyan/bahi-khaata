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
package com.bahikhaata.backend.inventory;

import com.bahikhaata.contracts.InventoryDetail;
import com.bahikhaata.contracts.InventoryRow;
import com.bahikhaata.contracts.SetBinRequest;
import com.bahikhaata.contracts.SetBinResult;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Inventory screen's endpoints: the aggregate table, one product's detail, and the one write
 * this feature adds — a batch's bin (design decision D8 of palletworks-inventory). The list and
 * detail reads are unfiltered on purpose: filtering, totals, and CSV export all happen client-side
 * against the loaded rows (design decisions D9/D10), so there is nothing to pass as query
 * parameters here.
 */
@RestController
@RequestMapping("/api/inventory")
class InventoryController {

    private final InventoryService inventory;

    InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    List<InventoryRow> rows() {
        return inventory.rows();
    }

    @GetMapping("/product/{id}")
    ResponseEntity<InventoryDetail> detail(@PathVariable UUID id) {
        return inventory.detail(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/batch/{id}/bin")
    ResponseEntity<SetBinResult> setBin(@PathVariable UUID id, @RequestBody SetBinRequest request) {
        return inventory
                .setBin(id, request.bin())
                .map(batch -> ResponseEntity.ok(new SetBinResult(id, batch.getBin())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
