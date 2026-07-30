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
import com.bahikhaata.contracts.Gstin;
import com.bahikhaata.contracts.UpdateSupplierRequest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rules a supplier lives by: unique identity, GSTIN validity, soft retirement, and the
 * guarantee the receipt path leans on that a lot's supplier exists and is active.
 *
 * <p>Uniqueness is enforced here as well as by the database indexes, so a duplicate fails with a
 * clear message rather than a raw constraint violation. The two keys are checked in their own
 * lanes: the normalised name always, and the GSTIN only when one is given.
 */
@Service
public class SupplierService {

    private final SupplierRepository suppliers;

    SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Transactional
    public Supplier create(CreateSupplierRequest request) {
        Gstin.requireValidOrAbsent(request.gstin());
        requireNameFree(request.name(), null);
        requireGstinFree(request.gstin(), null);
        return suppliers.save(
                new Supplier(
                        request.name(),
                        request.gstin(),
                        request.phone(),
                        request.address(),
                        request.contactPerson(),
                        request.notes()));
    }

    @Transactional
    public Supplier update(UUID id, UpdateSupplierRequest request) {
        Supplier supplier = require(id);
        Gstin.requireValidOrAbsent(request.gstin());
        requireNameFree(request.name(), id);
        requireGstinFree(request.gstin(), id);
        supplier.setName(request.name());
        supplier.setGstin(request.gstin());
        supplier.setPhone(request.phone());
        supplier.setAddress(request.address());
        supplier.setContactPerson(request.contactPerson());
        supplier.setNotes(request.notes());
        return suppliers.save(supplier);
    }

    @Transactional
    public Supplier deactivate(UUID id) {
        Supplier supplier = require(id);
        supplier.deactivate();
        return suppliers.save(supplier);
    }

    @Transactional
    public Supplier reactivate(UUID id) {
        Supplier supplier = require(id);
        supplier.activate();
        return suppliers.save(supplier);
    }

    @Transactional(readOnly = true)
    public List<Supplier> list(boolean activeOnly, String search) {
        List<Supplier> all =
                activeOnly ? suppliers.findByActiveTrueOrderByNameAsc() : suppliers.findAllByOrderByNameAsc();
        if (search == null || search.isBlank()) {
            return all;
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        return all.stream().filter(s -> matches(s, needle)).toList();
    }

    @Transactional(readOnly = true)
    public Supplier require(UUID id) {
        return suppliers.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("no such supplier: " + id));
    }

    /**
     * Resolves the supplier a lot is being received against, rejecting anything the receipt path
     * must never link to: a missing id, one that matches no supplier, or a retired one.
     */
    @Transactional(readOnly = true)
    public Supplier resolveActiveSupplier(String supplierId) {
        if (supplierId == null || supplierId.isBlank()) {
            throw new IllegalArgumentException("supplierId required");
        }
        UUID id;
        try {
            id = UUID.fromString(supplierId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("supplierId is not a valid id: " + supplierId);
        }
        Supplier supplier = require(id);
        if (!supplier.isActive()) {
            throw new IllegalArgumentException(
                    "supplier " + supplier.getName() + " is deactivated and cannot receive stock");
        }
        return supplier;
    }

    private boolean matches(Supplier s, String needle) {
        if (s.getName().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return s.getGstin() != null && s.getGstin().toLowerCase(Locale.ROOT).contains(needle);
    }

    private void requireNameFree(String name, UUID selfId) {
        Optional<Supplier> existing = suppliers.findByNameNormalized(Supplier.normalize(name));
        if (existing.isPresent() && !existing.get().getId().equals(selfId)) {
            throw new IllegalArgumentException("a supplier named " + name.trim() + " already exists");
        }
    }

    private void requireGstinFree(String gstin, UUID selfId) {
        if (gstin == null || gstin.isBlank()) {
            return;
        }
        Optional<Supplier> existing = suppliers.findByGstin(gstin.trim());
        if (existing.isPresent() && !existing.get().getId().equals(selfId)) {
            throw new IllegalArgumentException("a supplier with GSTIN " + gstin.trim() + " already exists");
        }
    }
}
