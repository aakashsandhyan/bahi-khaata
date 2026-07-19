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
package com.bahikhaata.backend.catalog;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a scanned code to the product it identifies.
 *
 * <p>Resolution is strictly a read: an unrecognised code returns empty and writes nothing. A
 * scanner that produces a code the catalogue has never seen must never create a phantom
 * product — the first time a product is entered is a deliberate goods-in step, not a
 * side effect of scanning.
 */
@Service
public class BarcodeResolver {

    private final BarcodeRepository barcodes;

    BarcodeResolver(BarcodeRepository barcodes) {
        this.barcodes = barcodes;
    }

    /**
     * The product a code identifies, or empty when the code is unrecognised.
     *
     * <p>Read-only, so the "creates nothing" guarantee holds structurally: no write path
     * exists to reach from here.
     */
    @Transactional(readOnly = true)
    public Optional<Product> resolve(String code) {
        return barcodes.findProductByCode(code);
    }
}
