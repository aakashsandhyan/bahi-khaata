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

import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Recording a supplier's consignment from their parsed manifest. */
@RestController
@RequestMapping("/api/consignments")
class ConsignmentController {

    private final ConsignmentImporter importer;

    ConsignmentController(ConsignmentImporter importer) {
        this.importer = importer;
    }

    @PostMapping("/import")
    ResponseEntity<ImportResult> importConsignment(@RequestBody ImportConsignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(importer.importConsignment(request));
    }

    /**
     * A manifest that cannot be recorded is the operator's to fix — an unknown category, costs
     * that overshoot what was paid. The message says which, and nothing has been written.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
