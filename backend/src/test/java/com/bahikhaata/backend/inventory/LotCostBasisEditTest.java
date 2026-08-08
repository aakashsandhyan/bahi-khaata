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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.CostAnchor;
import com.bahikhaata.contracts.CostBasisStrategy;
import com.bahikhaata.contracts.CreateManualLotRequest;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.MultiplierBase;
import com.bahikhaata.contracts.StockCondition;
import com.bahikhaata.contracts.UpdateLotRequest;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * The cost basis on the lot's create/update endpoints: a declared basis is validated and echoed
 * back; an incomplete one is a 400 naming what's missing; editing an unfrozen lot's basis
 * re-derives and re-pins its batches; editing a lot whose stock has already sold is the same 409
 * every other lot edit is, since a changed basis would rewrite recorded cost of goods sold.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-cost-basis-edit.db")
@AutoConfigureMockMvc
@Transactional
class LotCostBasisEditTest {

    private static final Instant AT = Instant.parse("2026-08-08T09:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private LotRepository lots;
    @Autowired private SupplierRepository suppliers;
    @Autowired private GoodsInCounting counting;
    @Autowired private BatchRepository batches;
    @Autowired private StockLedgerRepository ledger;
    @Autowired private ProductRepository products;

    private String supplierId(String name) {
        return suppliers.save(new Supplier(name, null, null, null, null, null)).getId().toString();
    }

    @Test
    @DisplayName("A manual lot may declare a cost basis at creation, and it is echoed back")
    void createWithFlatBasisPersistsAndEchoes() throws Exception {
        String body =
                json.writeValueAsString(
                        new CreateManualLotRequest(
                                supplierId("Flat Liquidator"), "2026-08-01", 50_000_00L,
                                AllocationMethod.RELATIVE_MRP, null,
                                CostBasisStrategy.FLAT_PER_UNIT, null, 8_00L, null, null, null, List.of()));

        mockMvc.perform(post("/api/lots/manual").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.costBasisStrategy").value("FLAT_PER_UNIT"))
                .andExpect(jsonPath("$.flatUnitCostPaise").value(8_00));
    }

    @Test
    @DisplayName("An incomplete cost basis is rejected, naming what is missing")
    void createWithIncompleteBasisIsBadRequest() throws Exception {
        // PERCENT_OF_ANCHOR with no percentBp and no anchor.
        String body =
                json.writeValueAsString(
                        new CreateManualLotRequest(
                                supplierId("Incomplete Liquidator"), "2026-08-01", 50_000_00L,
                                AllocationMethod.RELATIVE_MRP, null,
                                CostBasisStrategy.PERCENT_OF_ANCHOR, null, null, null, null, null, List.of()));

        mockMvc.perform(post("/api/lots/manual").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("percentBp"));
    }

    @Test
    @DisplayName("A rate card with overlapping bands is rejected")
    void createWithOverlappingBandsIsBadRequest() throws Exception {
        String body =
                json.writeValueAsString(
                        new CreateManualLotRequest(
                                supplierId("Overlap Liquidator"), "2026-08-01", 50_000_00L,
                                AllocationMethod.RELATIVE_MRP, null,
                                CostBasisStrategy.MRP_RATE_RANGE, CostAnchor.MRP, null, null, null, null,
                                List.of(
                                        new com.bahikhaata.contracts.MrpRateBand(0, 600_00L, 50_00),
                                        new com.bahikhaata.contracts.MrpRateBand(500_00, 1_000_00L, 150_00))));

        mockMvc.perform(post("/api/lots/manual").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    private Product product(String name) {
        return products.save(
                new Product(name, com.bahikhaata.contracts.Category.of("KITCHEN"), java.util.Map.of()));
    }

    private String createFlatLot(String supplierId, long flatUnitCostPaise) throws Exception {
        String body =
                json.writeValueAsString(
                        new CreateManualLotRequest(
                                supplierId, "2026-08-01", 50_000_00L, AllocationMethod.RELATIVE_MRP, null,
                                CostBasisStrategy.FLAT_PER_UNIT, null, flatUnitCostPaise, null, null, null,
                                List.of()));
        MvcResult result =
                mockMvc.perform(post("/api/lots/manual").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isCreated())
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("Editing an unfrozen lot's basis re-derives and re-pins its batches")
    void editingBasisRePinsExistingBatches() throws Exception {
        String supplierId = supplierId("Re-pin Liquidator");
        String lotId = createFlatLot(supplierId, 10_00);

        Lot lot = lots.findById(UUID.fromString(lotId)).orElseThrow();
        Product product = product("Re-pinned widget");
        product = counting.receiveManual(lot, product, StockCondition.GOOD, 4, null, false, AT).getProduct();
        Batch before = batches.findByLotIdAndProductId(lot.getId(), product.getId()).get(0);
        assertThat(before.getAllocatedUnitCost()).isEqualTo(Money.ofPaise(10_00));

        String updateBody =
                json.writeValueAsString(
                        new UpdateLotRequest(
                                null, null, null, null, null, null,
                                CostBasisStrategy.FLAT_PER_UNIT, null, 25_00L, null, null, null, List.of()));
        mockMvc.perform(
                        put("/api/lots/{lotId}", lotId).contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flatUnitCostPaise").value(25_00));

        Batch after = batches.findByLotIdAndProductId(lot.getId(), product.getId()).get(0);
        assertThat(after.getAllocatedUnitCost())
                .as("the batch was not yet sold, so the new basis re-costs it")
                .isEqualTo(Money.ofPaise(25_00));
    }

    @Test
    @DisplayName("Editing the basis of a lot whose stock has already sold is refused")
    void editingBasisOnAFrozenLotIsConflict() throws Exception {
        String supplierId = supplierId("Frozen Liquidator");
        String lotId = createFlatLot(supplierId, 10_00);

        Lot lot = lots.findById(UUID.fromString(lotId)).orElseThrow();
        Product product = product("Frozen widget");
        Batch batch = counting.receiveManual(lot, product, StockCondition.GOOD, 4, null, false, AT);
        ledger.save(StockLedgerEntry.sale(batch.getProduct(), batch, 1, Money.ofRupees(30), AT));
        ledger.flush();

        String updateBody =
                json.writeValueAsString(
                        new UpdateLotRequest(
                                null, null, null, null, null, null,
                                CostBasisStrategy.FLAT_PER_UNIT, null, 99_00L, null, null, null, List.of()));

        mockMvc.perform(
                        put("/api/lots/{lotId}", lotId).contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isConflict());

        // Nothing moved: the pre-freeze cost still stands.
        Batch unchanged = batches.findById(batch.getId()).orElseThrow();
        assertThat(unchanged.getAllocatedUnitCost()).isEqualTo(Money.ofPaise(10_00));
    }

    @Test
    @DisplayName("The amount-paid cross-check is surfaced on the lot summary once a basis is declared")
    void costVarianceIsSurfacedOnTheSummary() throws Exception {
        String supplierId = supplierId("Variance Liquidator");
        // amountPaidPaise is fixed at 50_000_00 by createFlatLot; a flat cost that does not sum
        // to it leaves a variance to report — never blocking receiving or pricing.
        String lotId = createFlatLot(supplierId, 10_00);
        Lot lot = lots.findById(UUID.fromString(lotId)).orElseThrow();
        Product product = product("Variance widget");
        counting.receiveManual(lot, product, StockCondition.GOOD, 4, null, false, AT);

        mockMvc.perform(get("/api/lots"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[?(@.id=='" + lotId + "')].costReconciles").value(false))
                .andExpect(
                        jsonPath("$[?(@.id=='" + lotId + "')].costVariancePaise")
                                // 4 units at 10.00 = 40.00 pinned against 50,000.00 paid — a real,
                                // reported gap, never blocking. JsonPath reads a JSON number that
                                // fits an int back as an Integer, so the expectation must match
                                // that boxed type rather than a boxed Long.
                                .value((int) (50_000_00L - 40_00L)));
    }
}
