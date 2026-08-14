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
package com.bahikhaata.backend.tax;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin: viewing and editing the per-sub_category GST rates (basis points). */
@RestController
@RequestMapping("/api/admin/gst")
class GstRateController {

    private final GstRateService service;

    GstRateController(GstRateService service) {
        this.service = service;
    }

    /** Every sub-category with its current active rate in basis points (null if unset). */
    @GetMapping("/rates")
    List<GstRateService.RateRow> rates() {
        return service.rates();
    }

    /** Set a sub-category's rate — close the old active row, open a new one. */
    @PutMapping("/rates/{subCategory}")
    ResponseEntity<Void> setRate(
            @PathVariable String subCategory, @RequestBody SetRateRequest request) {
        service.setRate(subCategory, request.basisPoints());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    record SetRateRequest(int basisPoints) {}
}
