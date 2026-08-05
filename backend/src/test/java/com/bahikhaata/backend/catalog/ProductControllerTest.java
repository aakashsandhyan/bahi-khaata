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
package com.bahikhaata.backend.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.bahikhaata.backend.shelf.PriceHistory;
import com.bahikhaata.backend.shelf.PriceHistoryRepository;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.Origin;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-product-controller.db")
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository products;

    @Autowired
    private BarcodeRepository barcodes;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PriceHistoryRepository priceHistory;

    @Test
    @DisplayName("A known barcode resolves to its product")
    void knownBarcodeResolves() throws Exception {
        Product product = products.save(new Product("Steel kettle", Category.of("KITCHEN"), Map.of()));
        barcodes.save(new Barcode(product, "8901234567890", Origin.MANUFACTURER));

        mockMvc.perform(get("/api/products/by-barcode/{code}", "8901234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Steel kettle"))
                .andExpect(jsonPath("$.category").value("KITCHEN"));
    }

    @Test
    @DisplayName("An unrecognised barcode is 404, not an empty 200")
    void unknownBarcodeIsNotFound() throws Exception {
        // The caller must be unable to mistake an unknown scan for a sellable line.
        mockMvc.perform(get("/api/products/by-barcode/{code}", "NO-SUCH-CODE"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A created product comes back unpriced, with a null price rather than zero")
    void createdProductIsUnpriced() throws Exception {
        String body =
                json.writeValueAsString(
                        Map.of("name", "Gift box", "category", "GIFTING", "hsnCode", "4819"));

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Gift box"))
                .andExpect(jsonPath("$.hsnCode").value("4819"))
                .andExpect(jsonPath("$.sellingPricePaise").isEmpty());
    }

    @Test
    @DisplayName("Setting a price returns the priced product")
    void settingAPrice() throws Exception {
        Product product = products.save(new Product("Wall clock", Category.of("DECOR"), Map.of()));

        mockMvc.perform(
                        put("/api/products/{id}/price", product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sellingPricePaise\":15000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellingPricePaise").value(15000));
    }

    @Test
    @DisplayName("A non-positive price is refused as a bad request")
    void nonPositivePriceIsRejected() throws Exception {
        Product product = products.save(new Product("Bowl", Category.of("KITCHEN"), Map.of()));

        mockMvc.perform(
                        put("/api/products/{id}/price", product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sellingPricePaise\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Pricing a product that does not exist is 404")
    void pricingUnknownProductIsNotFound() throws Exception {
        mockMvc.perform(
                        put("/api/products/{id}/price", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sellingPricePaise\":15000}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Setting a price through the catalog inline edit journals the change")
    void settingAPriceJournals() throws Exception {
        Product product = products.save(new Product("Journal Wall clock", Category.of("DECOR"), Map.of()));

        mockMvc.perform(
                        put("/api/products/{id}/price", product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sellingPricePaise\":15000}"))
                .andExpect(status().isOk());

        List<PriceHistory> rows = priceHistory.findByProductIdOrderByCreatedAtDesc(product.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOldPrice()).as("first-ever set").isNull();
        assertThat(rows.get(0).getNewPrice()).isEqualTo(Money.ofPaise(15000));
    }

    @Test
    @DisplayName("Editing the category persists via PATCH /api/products/{id}/category")
    void settingCategoryPersists() throws Exception {
        Product product = products.save(new Product("Steel kettle", Category.of("KITCHEN"), Map.of()));

        mockMvc.perform(
                        patch("/api/products/{id}/category", product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"categoryCode\":\"HOME_ESSENTIALS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("HOME_ESSENTIALS"));

        assertThat(products.findById(product.getId()).orElseThrow().getCategory())
                .isEqualTo(Category.of("HOME_ESSENTIALS"));
    }

    @Test
    @DisplayName("Reclassifying a product that does not exist is 404")
    void reclassifyingUnknownProductIsNotFound() throws Exception {
        mockMvc.perform(
                        patch("/api/products/{id}/category", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"categoryCode\":\"KITCHEN\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("An unknown category is refused as a bad request, not a database fault")
    void unknownCategoryIsRejected() throws Exception {
        Product product = products.save(new Product("Steel kettle", Category.of("KITCHEN"), Map.of()));

        mockMvc.perform(
                        patch("/api/products/{id}/category", product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"categoryCode\":\"NO_SUCH_CATEGORY\"}"))
                .andExpect(status().isBadRequest());

        // Refused before anything was written — the product still carries its original category.
        assertThat(products.findById(product.getId()).orElseThrow().getCategory())
                .isEqualTo(Category.of("KITCHEN"));
    }
}
