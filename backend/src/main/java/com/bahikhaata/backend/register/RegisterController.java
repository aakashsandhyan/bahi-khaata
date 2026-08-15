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
package com.bahikhaata.backend.register;

import com.bahikhaata.contracts.RegisterCloseSummary;
import com.bahikhaata.contracts.RegisterStateView;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Register screen's API: both drawers' state, open, cash in/out, close. */
@RestController
@RequestMapping("/api/registers")
class RegisterController {

    record OpenRequest(String operatorName, long floatPaise) {}
    record MovementRequest(String direction, long amountPaise, String note) {}
    record CloseRequest(long countedPaise) {}

    private final RegisterService registers;

    RegisterController(RegisterService registers) {
        this.registers = registers;
    }

    @GetMapping
    List<RegisterStateView> state() {
        return RegisterService.REGISTERS.stream().map(this::stateOf).toList();
    }

    @PostMapping("/{name}/open")
    ResponseEntity<RegisterStateView> open(@PathVariable String name, @RequestBody OpenRequest req) {
        registers.open(name, req.operatorName(), req.floatPaise());
        return ResponseEntity.status(HttpStatus.CREATED).body(stateOf(name));
    }

    @PostMapping("/{name}/cash-movements")
    ResponseEntity<RegisterStateView> movement(@PathVariable String name, @RequestBody MovementRequest req) {
        registers.recordMovement(name, req.direction(), req.amountPaise(), req.note());
        return ResponseEntity.status(HttpStatus.CREATED).body(stateOf(name));
    }

    @PostMapping("/{name}/close")
    RegisterCloseSummary close(@PathVariable String name, @RequestBody CloseRequest req) {
        return registers.close(name, req.countedPaise());
    }

    private RegisterStateView stateOf(String name) {
        RegisterSession s = registers.openSession(name);
        if (s == null) {
            return new RegisterStateView(name, false, null, null, null, null, null);
        }
        return new RegisterStateView(
                name, true, s.getId(), s.getOperatorName(),
                s.getFloatAmount().paise(), registers.expectedCashPaise(s), s.getOpenedAt());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
