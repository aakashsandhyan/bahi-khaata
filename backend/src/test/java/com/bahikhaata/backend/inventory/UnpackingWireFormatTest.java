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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the terminal actually puts on the wire.
 *
 * <p>Written after a correction failed with a bare 400 from the framework, before any of this
 * system's code ran. The terminal sent an absent field as an empty string — which looks harmless
 * and is not, since an empty string is no enum value, so the request could not be read at all
 * and the operator was shown a JSON object about paths and timestamps.
 *
 * <p>The lesson is narrow and worth keeping: a field that is not there must not be sent. These
 * assertions exercise the bodies the terminal really builds, because the failure lives in the
 * gap between two programs and neither one alone can show it.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-wire.db")
@AutoConfigureMockMvc
@Transactional
class UnpackingWireFormatTest {

    @Autowired private MockMvc mvc;
    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private LotRepository lots;
    @Autowired private SupplierRepository suppliers;

    private UUID lineId;

    private String supplierId(String name) {
        return suppliers.findByNameNormalized(Supplier.normalize(name))
                .map(Supplier::getId)
                .orElseGet(() -> suppliers.save(new Supplier(name, null, null, null, null, null)).getId())
                .toString();
    }

    @BeforeEach
    void importAndCount() {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Sushil"), "2026-07-17",
                        List.of(new ImportLot("KITCHEN", 10_000, AllocationMethod.RELATIVE_MRP,
                                List.of(new ImportLine("WIRE", "Wire test", 2, 1_000, null,
                                        "BOX-W", null, null))))));
        UUID lotId = lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b)
                .orElseThrow().getId();
        lineId = expectedLines.findByLotIdOrderByCode(lotId).get(0).getId();
        counting.countExpected(
                lineId, StockCondition.GOOD, 2, null, false,
                Instant.parse("2026-07-21T09:00:00Z"));
    }

    @Test
    @DisplayName("A correction that names no condition is accepted")
    void undoWithoutACondition() throws Exception {
        mvc.perform(
                        post("/api/unpacking/lines/{id}/undo", lineId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":1}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("A count that names no price is accepted")
    void countWithoutAnMrp() throws Exception {
        mvc.perform(
                        post("/api/unpacking/lines/{id}/count", lineId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":1,\"mrpIsEstimate\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("An empty string where an enum belongs is refused, as it should be")
    void emptyStringIsNotACondition() throws Exception {
        // Kept as a record of the original fault. The refusal is correct — the fix was to stop
        // sending this, not to start accepting it, because accepting it would mean guessing
        // what an operator meant.
        mvc.perform(
                        post("/api/unpacking/lines/{id}/undo", lineId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":1,\"condition\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
