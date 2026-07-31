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
package com.bahikhaata.backend.inventory;

import com.bahikhaata.contracts.CreateSupplierRequest;
import com.bahikhaata.contracts.SupplierResponse;
import com.bahikhaata.contracts.UpdateSupplierRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Managing the supplier master and viewing what each supplier delivered. */
@RestController
@RequestMapping("/api/suppliers")
class SupplierController {

    private final SupplierService suppliers;
    private final LotRepository lots;
    private final LotCategoryResolver lotCategories;

    SupplierController(SupplierService suppliers, LotRepository lots, LotCategoryResolver lotCategories) {
        this.suppliers = suppliers;
        this.lots = lots;
        this.lotCategories = lotCategories;
    }

    @GetMapping
    ResponseEntity<List<SupplierResponse>> list(
            @RequestParam(name = "active", defaultValue = "false") boolean activeOnly,
            @RequestParam(name = "search", required = false) String search) {
        return ResponseEntity.ok(
                suppliers.list(activeOnly, search).stream().map(SupplierController::toResponse).toList());
    }

    @GetMapping("/{id}")
    ResponseEntity<SupplierResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(suppliers.require(id)));
    }

    @PostMapping
    ResponseEntity<SupplierResponse> create(@RequestBody CreateSupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(suppliers.create(request)));
    }

    @PutMapping("/{id}")
    ResponseEntity<SupplierResponse> update(
            @PathVariable UUID id, @RequestBody UpdateSupplierRequest request) {
        return ResponseEntity.ok(toResponse(suppliers.update(id, request)));
    }

    @PostMapping("/{id}/deactivate")
    ResponseEntity<SupplierResponse> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(suppliers.deactivate(id)));
    }

    @PostMapping("/{id}/reactivate")
    ResponseEntity<SupplierResponse> reactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(suppliers.reactivate(id)));
    }

    @GetMapping("/{id}/lots")
    ResponseEntity<List<SupplierLotDto>> lotsFor(@PathVariable UUID id) {
        suppliers.require(id); // 400 rather than an empty list for an id that names no supplier
        Map<UUID, String> categoryByLot = lotCategories.categoryByLot();
        List<SupplierLotDto> result =
                lots.findBySupplierRefIdOrderByReceivedOnDesc(id).stream()
                        .map(
                                lot ->
                                        new SupplierLotDto(
                                                lot.getId(),
                                                lot.getReceivedOn(),
                                                lot.getAmountPaid().paise(),
                                                lot.isReceivingComplete(),
                                                lot.isManual(),
                                                categoryByLot.get(lot.getId())))
                        .toList();
        return ResponseEntity.ok(result);
    }

    private static SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(
                s.getId().toString(),
                s.getName(),
                s.getGstin(),
                s.getPhone(),
                s.getAddress(),
                s.getContactPerson(),
                s.getNotes(),
                s.isActive());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}

record SupplierLotDto(
        UUID id, LocalDate receivedOn, long amountPaidPaise, boolean receivingComplete,
        boolean isManual, String categoryCode) {}
