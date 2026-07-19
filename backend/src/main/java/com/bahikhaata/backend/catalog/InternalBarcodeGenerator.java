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

import com.bahikhaata.contracts.Origin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates a barcode for stock that arrives without a usable one.
 *
 * <p>Codes are {@code BBZ-100000}, {@code BBZ-100001}, … The {@code BBZ-} prefix is the whole
 * collision guarantee: a manufacturer barcode is an all-numeric EAN-13/UPC, and a value
 * containing letters and a hyphen can never equal one, so an internal code cannot be mistaken
 * for or collide with a real one. No number range has to be reserved or tracked.
 *
 * <p>The symbology is Code 128 — it encodes letters, digits, and the hyphen, which EAN-13
 * cannot. Rendering the code to a printed label is a later concern (labelling is out of scope
 * for this change); this produces the value and records it with {@link Origin#INTERNAL}.
 */
@Service
public class InternalBarcodeGenerator {

    static final String PREFIX = "BBZ-";
    static final long FIRST = 100_000L;
    static final long LAST = 999_999L;

    private final JdbcTemplate jdbc;
    private final BarcodeRepository barcodes;

    InternalBarcodeGenerator(JdbcTemplate jdbc, BarcodeRepository barcodes) {
        this.jdbc = jdbc;
        this.barcodes = barcodes;
    }

    /**
     * Allocates the next internal code and assigns it to the product.
     *
     * <p>The allocation is a single {@code UPDATE … RETURNING}, so concurrent goods-in cannot
     * hand out the same number. On exhaustion it throws, and the enclosing transaction rolls
     * back the increment — better a loud failure than a seventh digit that silently breaks
     * every printed label's width.
     */
    @Transactional
    public Barcode generateFor(Product product) {
        Long seq =
                jdbc.queryForObject(
                        "UPDATE internal_barcode_counter SET last_seq = last_seq + 1 "
                                + "WHERE id = 1 RETURNING last_seq",
                        Long.class);

        if (seq == null) {
            throw new IllegalStateException(
                    "internal_barcode_counter is missing its row; check that migrations applied");
        }
        if (seq > LAST) {
            throw new IllegalStateException(
                    "Internal barcode range exhausted at "
                            + PREFIX
                            + LAST
                            + "; widen the scheme before generating more codes");
        }

        return barcodes.save(new Barcode(product, PREFIX + seq, Origin.INTERNAL));
    }
}
