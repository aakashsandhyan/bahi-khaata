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
package com.bahikhaata.backend.customer;

import com.bahikhaata.contracts.CustomerViews.CustomerDetail;
import com.bahikhaata.contracts.CustomerViews.CustomerList;
import com.bahikhaata.contracts.CustomerViews.CustomerView;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customers: the counter's lookup/save (full mobile — it is what was just keyed), and the
 * Customers screen's list (masked mobiles) and detail (the customer's own record, full number).
 */
@RestController
@RequestMapping("/api/customers")
class CustomerController {

    record SaveCustomerRequest(String name, String mobile) {}

    private final CustomerService customers;
    private final CustomerQueries queries;

    CustomerController(CustomerService customers, CustomerQueries queries) {
        this.customers = customers;
        this.queries = queries;
    }

    /** The counter's lookup — 404 is an ordinary "new customer" answer, not an error state. */
    @GetMapping("/by-mobile/{mobile}")
    ResponseEntity<CustomerView> byMobile(@PathVariable String mobile) {
        return customers.findByMobile(mobile)
                .map(c -> ResponseEntity.ok(new CustomerView(c.getId(), c.getName(), c.getMobile())))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create by an unknown mobile; return the known customer with the name refreshed. */
    @PostMapping
    CustomerView save(@RequestBody SaveCustomerRequest req) {
        Customer c = customers.save(req.name(), req.mobile());
        return new CustomerView(c.getId(), c.getName(), c.getMobile());
    }

    /** The Customers screen: stats strip + rows, mobiles pre-masked. */
    @GetMapping
    CustomerList list() {
        return new CustomerList(queries.stats(), queries.rows());
    }

    @GetMapping("/{id}")
    CustomerDetail detail(@PathVariable UUID id) {
        return queries.detail(customers.require(id));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
