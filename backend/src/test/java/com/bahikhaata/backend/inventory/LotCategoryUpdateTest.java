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
import com.bahikhaata.contracts.AddProductRequest;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.CreateManualLotRequest;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.UpdateLotRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
 * The lot's own optional category — default for products added to it, still overridable per
 * product — and the update endpoint {@link LotEditPolicy} guards: editable until stock is
 * consumed, frozen after. Modelled on {@link LotEditPolicyTest} for the freeze fixture and
 * {@link com.bahikhaata.backend.catalog.ProductControllerTest} for the MockMvc/JSON style.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-lotcat.db")
@AutoConfigureMockMvc
@Transactional
class LotCategoryUpdateTest {

    private static final Instant WHEN = Instant.parse("2026-07-20T10:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private LotRepository lots;
    @Autowired private SupplierRepository suppliers;
    @Autowired private ProductRepository products;
    @Autowired private BatchRepository batches;
    @Autowired private StockLedgerRepository ledger;

    private String supplierId(String name) {
        return suppliers.save(new Supplier(name, null, null, null, null, null)).getId().toString();
    }

    private String createManualLot(String supplierId, String categoryCode) throws Exception {
        String body =
                json.writeValueAsString(
                        new CreateManualLotRequest(
                                supplierId, "2026-08-01", 50_000_00L, AllocationMethod.RELATIVE_MRP, categoryCode));
        MvcResult result =
                mockMvc.perform(post("/api/lots/manual").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isCreated())
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private Batch stockedBatch(Lot lot, String productName, long quantity) {
        Product product = products.save(new Product(productName, Category.of("KITCHEN"), Map.of()));
        Batch batch =
                batches.save(
                        new Batch(
                                product, lot, Money.ofRupees(120), CostBasis.ALLOCATED,
                                quantity, 0, Money.ofRupees(300), false));
        ledger.save(StockLedgerEntry.receiptOf(batch, WHEN));
        ledger.flush();
        return batch;
    }

    private Map<String, Object> lotSummary(String lotId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/lots")).andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> all =
                json.readValue(
                        result.getResponse().getContentAsString(), new TypeReference<List<Map<String, Object>>>() {});
        return all.stream()
                .filter(l -> lotId.equals(l.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("lot " + lotId + " not in list"));
    }

    @Test
    @DisplayName("A manual lot created with a category persists and echoes it back")
    void createWithCategoryPersistsAndEchoes() throws Exception {
        String supplierId = supplierId("Liquidator A");

        String body =
                json.writeValueAsString(
                        new CreateManualLotRequest(
                                supplierId, "2026-08-01", 50_000_00L, AllocationMethod.RELATIVE_MRP, "KITCHEN"));

        mockMvc.perform(post("/api/lots/manual").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryCode").value("KITCHEN"));
    }

    @Test
    @DisplayName("An unknown category on create is rejected")
    void createWithUnknownCategoryIsBadRequest() throws Exception {
        String supplierId = supplierId("Liquidator B");
        String body =
                json.writeValueAsString(
                        new CreateManualLotRequest(
                                supplierId, "2026-08-01", 50_000_00L, AllocationMethod.RELATIVE_MRP, "NOT_A_CATEGORY"));

        mockMvc.perform(post("/api/lots/manual").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A lot created without a category has none, until its products give it a derived one")
    void createWithoutCategoryIsNullThenDerived() throws Exception {
        String supplierId = supplierId("Liquidator C");
        String lotId = createManualLot(supplierId, null);

        assertThat(lotSummary(lotId).get("categoryCode")).isNull();

        String addBody =
                json.writeValueAsString(new AddProductRequest(null, "Steel bottle", 5, "GIFTING", null));
        mockMvc.perform(
                        post("/api/lots/{lotId}/add-product", lotId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(addBody))
                .andExpect(status().isCreated());

        // No stored category on the lot itself, so the list falls back to the derived value
        // now that a product names one.
        assertThat(lotSummary(lotId).get("categoryCode")).isEqualTo("GIFTING");
    }

    @Test
    @DisplayName("A lot's category can be updated, cleared, or rejected if unknown")
    void updateCategory() throws Exception {
        String supplierId = supplierId("Liquidator D");
        String lotId = createManualLot(supplierId, null);

        String setBody = json.writeValueAsString(new UpdateLotRequest(null, null, null, null, null, "GIFTING"));
        mockMvc.perform(put("/api/lots/{lotId}", lotId).contentType(MediaType.APPLICATION_JSON).content(setBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryCode").value("GIFTING"));

        String clearBody = json.writeValueAsString(new UpdateLotRequest(null, null, null, null, null, ""));
        mockMvc.perform(put("/api/lots/{lotId}", lotId).contentType(MediaType.APPLICATION_JSON).content(clearBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryCode").isEmpty());

        String unknownBody =
                json.writeValueAsString(new UpdateLotRequest(null, null, null, null, null, "NOT_A_CATEGORY"));
        mockMvc.perform(
                        put("/api/lots/{lotId}", lotId).contentType(MediaType.APPLICATION_JSON).content(unknownBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Every data-entry field on a lot can be corrected")
    void updateAppliesEveryPresentField() throws Exception {
        String originalSupplier = supplierId("Liquidator E");
        String correctedSupplier = supplierId("Liquidator E, corrected");
        String lotId = createManualLot(originalSupplier, null);

        String body =
                json.writeValueAsString(
                        new UpdateLotRequest(
                                correctedSupplier, "2026-08-05", 60_000_00L, 500_00L, AllocationMethod.IMPORTED, null));

        mockMvc.perform(put("/api/lots/{lotId}", lotId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplier").value("Liquidator E, corrected"))
                .andExpect(jsonPath("$.receivedOn").value("2026-08-05"));

        Lot updated = lots.findById(UUID.fromString(lotId)).orElseThrow();
        assertThat(updated.getAmountPaid()).isEqualTo(Money.ofPaise(60_000_00L));
        assertThat(updated.getFreight()).isEqualTo(Money.ofPaise(500_00L));
        assertThat(updated.getAllocationMethod()).isEqualTo(AllocationMethod.IMPORTED);
    }

    @Test
    @DisplayName("Editing a lot once stock has been consumed from it is refused")
    void updateFrozenLotIsConflict() throws Exception {
        Lot lot =
                lots.save(
                        new Lot(
                                "Liquidator F",
                                LocalDate.of(2026, 7, 1),
                                Money.ofRupees(50_000),
                                Money.ZERO,
                                AllocationMethod.RELATIVE_MRP));
        Batch batch = stockedBatch(lot, "Steel kettle", 100);
        ledger.save(StockLedgerEntry.sale(batch.getProduct(), batch, 2, Money.ofRupees(240), WHEN));
        ledger.flush();

        String body = json.writeValueAsString(new UpdateLotRequest(null, null, null, null, null, "KITCHEN"));

        mockMvc.perform(
                        put("/api/lots/{lotId}", lot.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Adding a product inherits the lot's default category unless it overrides it")
    void addProductInheritsOrOverridesTheLotDefault() throws Exception {
        String supplierId = supplierId("Liquidator G");
        String lotId = createManualLot(supplierId, "KITCHEN");

        // No categoryCode on the line: it inherits the lot's default.
        String inheritBody =
                json.writeValueAsString(new AddProductRequest(null, "Steel kettle", 5, null, null));
        mockMvc.perform(
                        post("/api/lots/{lotId}/add-product", lotId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(inheritBody))
                .andExpect(status().isCreated());
        assertThat(products.findAll())
                .filteredOn(p -> p.getName().equals("Steel kettle"))
                .extracting(Product::getCategory)
                .containsExactly(Category.of("KITCHEN"));

        // An explicit categoryCode on the line overrides the lot default — a mixed lot.
        String overrideBody =
                json.writeValueAsString(new AddProductRequest(null, "Wall clock", 3, "DECOR", null));
        mockMvc.perform(
                        post("/api/lots/{lotId}/add-product", lotId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(overrideBody))
                .andExpect(status().isCreated());
        assertThat(products.findAll())
                .filteredOn(p -> p.getName().equals("Wall clock"))
                .extracting(Product::getCategory)
                .containsExactly(Category.of("DECOR"));

        // The lot's own stored category is unaffected, and still stored-first in the list.
        assertThat(lotSummary(lotId).get("categoryCode")).isEqualTo("KITCHEN");
    }

    @Test
    @DisplayName("Adding a product with no category and no lot default is rejected")
    void addProductWithNoCategoryAndNoDefaultIsBadRequest() throws Exception {
        String supplierId = supplierId("Liquidator H");
        String lotId = createManualLot(supplierId, null);

        String body = json.writeValueAsString(new AddProductRequest(null, "Mystery item", 1, null, null));
        mockMvc.perform(
                        post("/api/lots/{lotId}/add-product", lotId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("The lot list prefers the lot's own stored category over the derived one")
    void listLotsIsStoredFirst() throws Exception {
        String supplierId = supplierId("Liquidator I");
        String lotId = createManualLot(supplierId, "KITCHEN");

        String addBody =
                json.writeValueAsString(new AddProductRequest(null, "Beach towel", 2, "OUTDOORS", null));
        mockMvc.perform(
                        post("/api/lots/{lotId}/add-product", lotId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(addBody))
                .andExpect(status().isCreated());

        // The lot's stored KITCHEN wins over the OUTDOORS its only product would derive to.
        assertThat(lotSummary(lotId).get("categoryCode")).isEqualTo("KITCHEN");
    }
}
