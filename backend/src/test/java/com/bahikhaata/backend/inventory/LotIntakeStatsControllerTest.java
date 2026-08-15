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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Intake screen's one read-only aggregate ({@code GET /api/lots/{lotId}/stats}) — the header
 * stats and lot-math-rail figures for a single lot (design decision D5 of palletworks-intake).
 *
 * <p>Covers the three shapes task 1.2 names: a populated lot (every figure real), an empty lot
 * (every nullable figure null, nothing divides by zero), and a manual lot with a product added but
 * not yet counted (expected is real, counted stays zero, so the ratios stay honest dashes).
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-lot-intake-stats.db")
@AutoConfigureMockMvc
@Transactional
class LotIntakeStatsControllerTest {

    private static final Instant AT = Instant.parse("2026-08-06T09:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private LotRepository lots;
    @Autowired private SupplierRepository suppliers;
    @Autowired private com.bahikhaata.backend.catalog.BarcodeRepository barcodes;
    @Autowired private com.bahikhaata.backend.catalog.ProductRepository products;
    @Autowired private ObjectMapper json;

    private String supplierId(String name) {
        return suppliers.findByNameNormalized(Supplier.normalize(name))
                .map(Supplier::getId)
                .orElseGet(() -> suppliers.save(new Supplier(name, null, null, null, null, null)).getId())
                .toString();
    }

    private UUID importLot(long paidPaise, List<ImportLine> lines) {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Sushil"), "2026-08-01",
                        List.of(new ImportLot("KITCHEN", paidPaise,
                                AllocationMethod.RELATIVE_MRP, lines))));
        return lots.findAll().stream()
                .filter(Lot::isOpen).reduce((a, b) -> b).orElseThrow().getId();
    }

    private static ImportLine line(String box, String code, long qty, long unitValuePaise) {
        return new ImportLine(code, code, qty, unitValuePaise, null, box, null, null);
    }

    private ExpectedLine lineFor(UUID lotId, String code) {
        return expectedLines.findByLotIdOrderByCode(lotId).stream()
                .filter(l -> l.getCode().equals(code)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("A populated lot reports paid, MRP found, cost-of-MRP%, counted/expected, and effective cost/unit")
    void populatedLot() throws Exception {
        UUID lot = importLot(40_000, List.of(line("BOX-1", "A", 4, 10_000)));
        counting.countExpected(
                lineFor(lot, "A").getId(), 4, Money.ofPaise(12_000), false, AT);

        // Priced, so its counted units contribute to projected retail; unpriced goods would not.
        UUID productId = barcodes.findByCode("A").orElseThrow().getProduct().getId();
        Product product = products.findById(productId).orElseThrow();
        product.setSellingPrice(Money.ofPaise(15_000));
        products.save(product);

        mockMvc.perform(get("/api/lots/{lotId}/stats", lot))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidPaise").value(40_000))
                .andExpect(jsonPath("$.mrpFoundPaise").value(4 * 12_000))
                .andExpect(jsonPath("$.costOfMrpPercent").value(83)) // round(40000*100/48000)
                .andExpect(jsonPath("$.expectedUnits").value(4))
                .andExpect(jsonPath("$.countedUnits").value(4))
                .andExpect(jsonPath("$.shortUnits").value(0))
                .andExpect(jsonPath("$.overUnits").value(0))
                .andExpect(jsonPath("$.effectiveCostPerUnitPaise").value(10_000))
                .andExpect(jsonPath("$.projectedRetailPaise").value(4 * 15_000));
    }

    @Test
    @DisplayName("A short line and an over line both contribute to the lot's short/over totals")
    void shortAndOverLines() throws Exception {
        UUID lot = importLot(100_000, List.of(
                line("BOX-1", "SHORT", 10, 10_000),
                line("BOX-1", "OVER", 5, 10_000)));
        counting.countExpected(lineFor(lot, "SHORT").getId(), 6, null, false, AT);
        counting.countExpected(lineFor(lot, "OVER").getId(), 8, null, false, AT);

        mockMvc.perform(get("/api/lots/{lotId}/stats", lot))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedUnits").value(15))
                .andExpect(jsonPath("$.countedUnits").value(14))
                .andExpect(jsonPath("$.shortUnits").value(4)) // 10 - 6
                .andExpect(jsonPath("$.overUnits").value(3)); // 8 - 5
    }

    @Test
    @DisplayName("An empty lot answers honest nulls, never a divide-by-zero")
    void emptyLot() throws Exception {
        String body = """
                {"supplierId":"%s","receivedOn":"2026-08-01","amountPaidPaise":5000,"allocationMethod":"RELATIVE_MRP"}
                """.formatted(supplierId("Empty Lot Supplier"));

        String response = mockMvc.perform(
                        post("/api/lots/manual").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID lotId = UUID.fromString(json.readTree(response).get("id").asText());

        mockMvc.perform(get("/api/lots/{lotId}/stats", lotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidPaise").value(5000))
                .andExpect(jsonPath("$.pinnedPaise").value(0))
                .andExpect(jsonPath("$.mrpFoundPaise").value(0))
                .andExpect(jsonPath("$.costOfMrpPercent").doesNotExist())
                .andExpect(jsonPath("$.expectedUnits").doesNotExist())
                .andExpect(jsonPath("$.countedUnits").value(0))
                .andExpect(jsonPath("$.shortUnits").value(0))
                .andExpect(jsonPath("$.overUnits").value(0))
                .andExpect(jsonPath("$.effectiveCostPerUnitPaise").doesNotExist())
                .andExpect(jsonPath("$.projectedRetailPaise").value(0));
    }

    @Test
    @DisplayName("A manual lot with a product added but not yet counted stays short, with no effective cost")
    void manualLotAwaitingCount() throws Exception {
        String createBody = """
                {"supplierId":"%s","receivedOn":"2026-08-01","amountPaidPaise":9000,"allocationMethod":"RELATIVE_MRP"}
                """.formatted(supplierId("Manual Lot Supplier"));
        String createResponse = mockMvc.perform(
                        post("/api/lots/manual").contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID lotId = UUID.fromString(json.readTree(createResponse).get("id").asText());

        String addProductBody = """
                {"code":null,"name":"Manual Product","quantity":3,"categoryCode":"KITCHEN","estimatedCostPaise":1000}
                """;
        mockMvc.perform(
                        post("/api/lots/{lotId}/add-product", lotId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(addProductBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/lots/{lotId}/stats", lotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidPaise").value(9000))
                .andExpect(jsonPath("$.mrpFoundPaise").value(0))
                .andExpect(jsonPath("$.costOfMrpPercent").doesNotExist())
                .andExpect(jsonPath("$.expectedUnits").value(3))
                .andExpect(jsonPath("$.countedUnits").value(0))
                .andExpect(jsonPath("$.shortUnits").value(3))
                .andExpect(jsonPath("$.overUnits").value(0))
                .andExpect(jsonPath("$.effectiveCostPerUnitPaise").doesNotExist());
    }

    @Test
    @DisplayName("An unknown lot id is a bad request, not a 500")
    void unknownLot() throws Exception {
        mockMvc.perform(get("/api/lots/{lotId}/stats", UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }
}
