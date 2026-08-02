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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.CostBasis;
import com.bahikhaata.contracts.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-stock-controller.db")
@AutoConfigureMockMvc
@Transactional
class StockControllerTest {

    private static final Instant JUNE_10 = Instant.parse("2026-06-10T10:00:00Z");
    private static final Instant JULY_20 = Instant.parse("2026-07-20T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository products;

    @Autowired
    private LotRepository lots;

    @Autowired
    private BatchRepository batches;

    @Autowired
    private StockLedgerRepository ledger;

    private Product newProduct(String name) {
        return products.save(new Product(name, Category.of("KITCHEN"), Map.of()));
    }

    private Batch stockedBatch(
            Product product, LocalDate receivedOn, long quantity, Money unitCost, Instant when) {
        Lot lot =
                lots.save(
                        new Lot(
                                "Liquidator",
                                receivedOn,
                                Money.ofRupees(10_000),
                                Money.ZERO,
                                AllocationMethod.RELATIVE_MRP));
        Batch batch =
                batches.save(
                        new Batch(
                                product, lot, unitCost, CostBasis.ALLOCATED,
                                quantity, 0, Money.ofRupees(300), false));
        ledger.save(StockLedgerEntry.receiptOf(batch, when));
        ledger.flush();
        return batch;
    }

    @Test
    @DisplayName("Quantity on hand is reported for a product")
    void reportsOnHand() throws Exception {
        Product product = newProduct("Steel kettle");
        stockedBatch(product, LocalDate.of(2026, 6, 10), 100, Money.ofRupees(120), JUNE_10);

        mockMvc.perform(get("/api/stock/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(100))
                // No asAt asked for, so none reported: the figure is simply current.
                .andExpect(jsonPath("$.asAt").isEmpty());
    }

    @Test
    @DisplayName("A past position is reported when asAt is given")
    void reportsOnHandAsAt() throws Exception {
        Product product = newProduct("Steel bottle");
        Batch batch =
                stockedBatch(product, LocalDate.of(2026, 6, 10), 100, Money.ofRupees(120), JUNE_10);
        ledger.save(
                StockLedgerEntry.sale(product, batch, 30, Money.ofRupees(3_600), JULY_20));
        ledger.flush();

        mockMvc.perform(
                        get("/api/stock/products/{id}", product.getId())
                                .param("asAt", "2026-07-01T00:00:00Z"))
                .andExpect(status().isOk())
                // Before the sale, all hundred were still on the shelf.
                .andExpect(jsonPath("$.quantityOnHand").value(100));

        mockMvc.perform(get("/api/stock/products/{id}", product.getId()))
                .andExpect(jsonPath("$.quantityOnHand").value(70));
    }

    @Test
    @DisplayName("Batches are listed oldest first, with what remains and what each cost")
    void listsBatchesOldestFirst() throws Exception {
        Product product = newProduct("Steel bottle");
        stockedBatch(product, LocalDate.of(2026, 7, 1), 10, Money.ofRupees(150), JULY_20);
        stockedBatch(product, LocalDate.of(2026, 6, 10), 5, Money.ofRupees(120), JUNE_10);

        mockMvc.perform(get("/api/stock/products/{id}/batches", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // June's delivery first, despite being created second.
                .andExpect(jsonPath("$[0].receivedOn").value("2026-06-10"))
                .andExpect(jsonPath("$[0].quantityRemaining").value(5))
                .andExpect(jsonPath("$[0].allocatedUnitCostPaise").value(12000))
                .andExpect(jsonPath("$[1].receivedOn").value("2026-07-01"))
                .andExpect(jsonPath("$[1].allocatedUnitCostPaise").value(15000));
    }

    @Test
    @DisplayName("An exhausted batch is left out of the breakdown")
    void exhaustedBatchesAreOmitted() throws Exception {
        Product product = newProduct("Sold out batch");
        Batch june =
                stockedBatch(product, LocalDate.of(2026, 6, 10), 5, Money.ofRupees(120), JUNE_10);
        stockedBatch(product, LocalDate.of(2026, 7, 1), 10, Money.ofRupees(150), JULY_20);
        ledger.save(StockLedgerEntry.sale(product, june, 5, Money.ofRupees(600), JULY_20));
        ledger.flush();

        mockMvc.perform(get("/api/stock/products/{id}/batches", product.getId()))
                // A caller asking what is on the shelf does not want a list padded with zeroes.
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].receivedOn").value("2026-07-01"));
    }

    @Test
    @DisplayName("Valuation is reported at each batch's own cost")
    void reportsValuation() throws Exception {
        Product product = newProduct("Steel bottle");
        stockedBatch(product, LocalDate.of(2026, 6, 10), 10, Money.ofRupees(120), JUNE_10);
        stockedBatch(product, LocalDate.of(2026, 7, 1), 10, Money.ofRupees(150), JULY_20);

        mockMvc.perform(get("/api/stock/products/{id}/valuation", product.getId()))
                .andExpect(status().isOk())
                // 10 × ₹120 + 10 × ₹150 = ₹2,700, in paise.
                .andExpect(jsonPath("$.valuationPaise").value(270000));
    }

    @Test
    @DisplayName("An unknown product is 404, not a confident zero")
    void unknownProductIsNotFound() throws Exception {
        // Reporting zero stock for a product that does not exist would read as a fact.
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(get("/api/stock/products/{id}", unknown)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/stock/products/{id}/batches", unknown))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/stock/products/{id}/valuation", unknown))
                .andExpect(status().isNotFound());
    }
}
