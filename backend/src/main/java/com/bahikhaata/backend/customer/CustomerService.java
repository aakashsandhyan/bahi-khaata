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

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer identity for the counter. The mobile is the key: normalized to exactly ten digits
 * (country code and punctuation stripped) so "+91 98214 55120" and "9821455120" are one customer.
 * Saving by a known mobile refreshes the name — the counter's latest utterance is the freshest
 * record ("it's Meera, not Mira") — and a blank name never clobbers a stored one.
 */
@Service
public class CustomerService {

    private final CustomerRepository customers;

    CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    /**
     * Ten digits starting 6–9, or refused. Strips spaces, punctuation, and a leading country code
     * (+91 / 91 / 0) before judging — the counter keys numbers however the customer says them.
     */
    public static String normalizeMobile(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("A mobile number is required.");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!digits.matches("[6-9]\\d{9}")) {
            throw new IllegalArgumentException(
                    "\"" + raw.trim() + "\" is not a ten-digit mobile number.");
        }
        return digits;
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findByMobile(String rawMobile) {
        return customers.findByMobile(normalizeMobile(rawMobile));
    }

    /**
     * Create by an unknown mobile; return the existing customer by a known one, refreshing the
     * stored name when a different non-blank name was keyed.
     */
    @Transactional
    public Customer save(String name, String rawMobile) {
        String mobile = normalizeMobile(rawMobile);
        Optional<Customer> existing = customers.findByMobile(mobile);
        if (existing.isPresent()) {
            Customer customer = existing.get();
            if (name != null && !name.isBlank() && !name.trim().equals(customer.getName())) {
                customer.refreshName(name.trim());
                customers.save(customer);
            }
            return customer;
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A new customer needs a name.");
        }
        return customers.save(new Customer(name.trim(), mobile));
    }

    /** The customer a completing sale attaches — by id, must exist. */
    @Transactional(readOnly = true)
    public Customer require(UUID customerId) {
        return customers.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("No such customer."));
    }
}
