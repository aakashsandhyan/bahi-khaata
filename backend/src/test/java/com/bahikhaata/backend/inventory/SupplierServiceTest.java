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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.contracts.CreateSupplierRequest;
import com.bahikhaata.contracts.UpdateSupplierRequest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-supplier-service.db")
@Transactional
class SupplierServiceTest {

    @Autowired private SupplierService service;
    @Autowired private SupplierRepository suppliers;

    private static CreateSupplierRequest create(String name, String gstin) {
        return new CreateSupplierRequest(name, gstin, null, null, null, null);
    }

    private static final String GSTIN_A = "22AAAAA0000A1Z5";
    private static final String GSTIN_B = "27BBBBB1111B2Z6";

    @Test
    @DisplayName("A supplier with only a name is created, active, with no GSTIN")
    void createsNameOnly() {
        Supplier s = service.create(create("Sushil Traders", null));
        assertThat(s.getName()).isEqualTo("Sushil Traders");
        assertThat(s.getGstin()).isNull();
        assertThat(s.isActive()).isTrue();
    }

    @Test
    @DisplayName("A name that normalises to an existing one is rejected as a duplicate")
    void rejectsDuplicateNormalisedName() {
        service.create(create("ABC Traders", null));
        assertThatThrownBy(() -> service.create(create("  abc   traders ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Two suppliers without a GSTIN are both allowed")
    void allowsMultipleWithoutGstin() {
        service.create(create("Vendor One", null));
        service.create(create("Vendor Two", null));
        assertThat(suppliers.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("The same GSTIN on a second supplier is rejected")
    void rejectsDuplicateGstin() {
        service.create(create("First", GSTIN_A));
        assertThatThrownBy(() -> service.create(create("Second", GSTIN_A)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GSTIN");
    }

    @Test
    @DisplayName("A malformed GSTIN is rejected")
    void rejectsMalformedGstin() {
        assertThatThrownBy(() -> service.create(create("Bad GSTIN", "NOT-A-GSTIN")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Deactivation hides from the active list but keeps the record; reactivation restores it")
    void softDeleteAndReactivate() {
        Supplier s = service.create(create("Retire Me", null));
        service.deactivate(s.getId());
        assertThat(service.require(s.getId()).isActive()).isFalse();
        assertThat(service.list(true, null)).noneMatch(x -> x.getId().equals(s.getId()));
        assertThat(service.list(false, null)).anyMatch(x -> x.getId().equals(s.getId()));

        service.reactivate(s.getId());
        assertThat(service.require(s.getId()).isActive()).isTrue();
    }

    @Test
    @DisplayName("An edit can move a GSTIN onto a supplier that had none, and keep its own GSTIN")
    void updateKeepsOwnGstin() {
        Supplier s = service.create(create("Editable", GSTIN_A));
        // Re-saving with its own GSTIN must not trip the uniqueness guard against itself.
        Supplier updated =
                service.update(
                        s.getId(),
                        new UpdateSupplierRequest("Editable Renamed", GSTIN_A, "99999", null, null, null));
        assertThat(updated.getName()).isEqualTo("Editable Renamed");
        assertThat(updated.getGstin()).isEqualTo(GSTIN_A);
        assertThat(updated.getPhone()).isEqualTo("99999");
    }

    @Test
    @DisplayName("An edit onto another supplier's GSTIN is rejected")
    void updateRejectsOthersGstin() {
        service.create(create("Holder", GSTIN_A));
        Supplier other = service.create(create("Other", GSTIN_B));
        assertThatThrownBy(
                        () ->
                                service.update(
                                        other.getId(),
                                        new UpdateSupplierRequest("Other", GSTIN_A, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GSTIN");
    }

    @Test
    @DisplayName("Search matches on name and on GSTIN")
    void searchMatchesNameAndGstin() {
        service.create(create("Kitchen Kart", GSTIN_A));
        service.create(create("Fashion Hub", GSTIN_B));
        assertThat(service.list(false, "kitchen")).extracting(Supplier::getName).containsExactly("Kitchen Kart");
        assertThat(service.list(false, GSTIN_B)).extracting(Supplier::getName).containsExactly("Fashion Hub");
    }

    @Test
    @DisplayName("resolveActiveSupplier rejects a blank, unknown, malformed, or inactive id and accepts a live one")
    void resolveActiveSupplierGuards() {
        assertThatThrownBy(() -> service.resolveActiveSupplier(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolveActiveSupplier("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolveActiveSupplier(UUID.randomUUID().toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no such supplier");

        Supplier retired = service.create(create("Retired", null));
        service.deactivate(retired.getId());
        assertThatThrownBy(() -> service.resolveActiveSupplier(retired.getId().toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deactivated");

        Supplier live = service.create(create("Live", null));
        assertThat(service.resolveActiveSupplier(live.getId().toString()).getId()).isEqualTo(live.getId());
    }
}
