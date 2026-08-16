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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The identity rules: one customer per normalized number, latest name wins, blanks never clobber. */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customers;

    private CustomerService service() {
        return new CustomerService(customers);
    }

    @Test
    void everyWayOfSayingTheNumberIsOneCustomer() {
        for (String raw : new String[] {
                "+91 98214 55120", "9821455120", "91-9821455120", "098214 55120", "98214-55120"}) {
            assertThat(CustomerService.normalizeMobile(raw)).isEqualTo("9821455120");
        }
    }

    @Test
    void nonMobilesAreRefused() {
        // A metro landline (leading 0 stripped, starts 1-5) is refused. A limitation accepted in
        // the design: an 11-digit landline whose subscriber number happens to start 6-9 (e.g.
        // 0755-7xxxxxx) is indistinguishable from a 0-prefixed mobile and would pass — the
        // counter keys mobiles, so the strip-0 habit wins over that edge.
        for (String raw : new String[] {"12345", "011-23456789", "5821455120", "", "98214551201"}) {
            assertThatThrownBy(() -> CustomerService.normalizeMobile(raw))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void aKnownMobileRefreshesTheName() {
        Customer existing = new Customer("Mira Joshi", "9821455120");
        when(customers.findByMobile("9821455120")).thenReturn(Optional.of(existing));
        when(customers.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Customer result = service().save("Meera Joshi", "+91 98214 55120");

        assertThat(result.getName()).isEqualTo("Meera Joshi");
        verify(customers).save(existing);
    }

    @Test
    void aBlankNameNeverClobbersAStoredOne() {
        Customer existing = new Customer("Meera Joshi", "9821455120");
        when(customers.findByMobile("9821455120")).thenReturn(Optional.of(existing));

        Customer result = service().save("  ", "9821455120");

        assertThat(result.getName()).isEqualTo("Meera Joshi");
        verify(customers, never()).save(any());
    }

    @Test
    void aNewCustomerNeedsAName() {
        when(customers.findByMobile("9821455120")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().save(" ", "9821455120"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void theListMaskShowsOnlyTheLastFour() {
        assertThat(CustomerQueries.mask("9821455120")).isEqualTo("•••• 5120");
    }
}
