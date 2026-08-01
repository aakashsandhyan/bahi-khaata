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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The in-hand count taken at pricing is the count of record, so pricing reconciles the batch to it.
 * A first pricing overwrites on-hand to the true total (up or down from the rough unpacking count);
 * a later pricing adds the extra pieces found (plus-only); leaving the figure alone moves nothing.
 * Cost is pinned at receipt, so the pinned total must re-scale with the quantity in every case.
 *
 * <p>Each lot is bought at exactly its stated total, so the lot rate is 1.0 and the pinned unit
 * cost equals the stated value — that keeps the cost assertions readable.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-in-hand-reconciliation.db")
@Transactional
class InHandReconciliationTest {

    private static final Instant AT = Instant.parse("2026-07-21T09:00:00Z");
    private static final long UNIT_VALUE = 10_000; // pinned unit cost, rate being 1.0

    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BatchRepository batches;
    @Autowired private BarcodeRepository barcodes;
    @Autowired private LotRepository lots;
    @Autowired private StockLevels stock;
    @Autowired private SupplierRepository suppliers;

    private String supplierId() {
        return suppliers.findByNameNormalized(Supplier.normalize("Sushil"))
                .map(Supplier::getId)
                .orElseGet(() -> suppliers.save(new Supplier("Sushil", null, null, null, null, null)).getId())
                .toString();
    }

    /** Imports one lot bought at its stated total (rate 1.0), one line, and returns its id. */
    private UUID importLot(String code, long qty) {
        importer.importConsignment(new ImportConsignmentRequest(
                supplierId(), "2026-07-17",
                List.of(new ImportLot("KITCHEN", UNIT_VALUE * qty, AllocationMethod.RELATIVE_MRP,
                        List.of(new ImportLine(code, code, qty, UNIT_VALUE, null, "BOX-1", null, null))))));
        return lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b).orElseThrow().getId();
    }

    private void count(UUID lotId, String code, long quantity) {
        UUID lineId = expectedLines.findByLotIdOrderByCode(lotId).stream()
                .filter(l -> l.getCode().equals(code)).findFirst().orElseThrow().getId();
        counting.countExpected(lineId, quantity, null, false, AT);
    }

    private Batch batchFor(UUID lotId, String code) {
        UUID productId = barcodes.findByCode(code).orElseThrow().getProduct().getId();
        return batches.findByLotIdAndProductIdAndCondition(lotId, productId, StockCondition.GOOD)
                .orElseThrow();
    }

    @Test
    @DisplayName("First pricing above the rough count lifts on-hand and re-pins the cost up")
    void firstPricingAboveCountLiftsStock() {
        UUID lot = importLot("KADAI", 11);
        count(lot, "KADAI", 8); // unpacking found only 8 of the 11 manifested
        Batch batch = batchFor(lot, "KADAI");

        long delta = counting.reconcileBatchTo(batch, 10, AT.plusSeconds(60));

        assertEquals(2, delta, "reconciled up by 2 to the true in-hand total");
        assertEquals(10, stock.onHandForBatch(batch.getId()), "on-hand is the in-hand total");
        assertEquals(10, batch.getQuantityReceived());
        assertEquals(UNIT_VALUE * 10, batch.getAllocatedTotal().paise(), "pinned total re-scaled up");
        assertEquals(UNIT_VALUE, batch.getAllocatedUnitCost().paise(), "unit cost unchanged");
    }

    @Test
    @DisplayName("First pricing below the rough count drops on-hand and re-pins the cost down")
    void firstPricingBelowCountDropsStock() {
        UUID lot = importLot("KADAI", 11);
        count(lot, "KADAI", 8);
        Batch batch = batchFor(lot, "KADAI");

        long delta = counting.reconcileBatchTo(batch, 5, AT.plusSeconds(60));

        assertEquals(-3, delta, "reconciled down by 3 to the true in-hand total");
        assertEquals(5, stock.onHandForBatch(batch.getId()));
        assertEquals(5, batch.getQuantityReceived());
        assertEquals(UNIT_VALUE * 5, batch.getAllocatedTotal().paise(), "pinned total re-scaled down");
        assertEquals(UNIT_VALUE, batch.getAllocatedUnitCost().paise(), "unit cost unchanged");
    }

    @Test
    @DisplayName("Reconciling to the count already on hand moves nothing")
    void reconcilingToTheSameCountMovesNothing() {
        UUID lot = importLot("KADAI", 8);
        count(lot, "KADAI", 8);
        Batch batch = batchFor(lot, "KADAI");

        long delta = counting.reconcileBatchTo(batch, 8, AT.plusSeconds(60));

        assertEquals(0, delta, "no delta, so no ledger movement");
        assertEquals(8, stock.onHandForBatch(batch.getId()));
        assertEquals(UNIT_VALUE * 8, batch.getAllocatedTotal().paise());
    }

    @Test
    @DisplayName("A later pricing adds the extra pieces found on top of what is on hand")
    void laterPricingAddsFoundPieces() {
        UUID lot = importLot("KADAI", 11);
        count(lot, "KADAI", 4);
        Batch batch = batchFor(lot, "KADAI");
        counting.reconcileBatchTo(batch, 4, AT.plusSeconds(60)); // first pricing set 4

        counting.addToInHand(batch, 3, AT.plusSeconds(120)); // scanned again, 3 more found

        assertEquals(7, stock.onHandForBatch(batch.getId()), "4 + 3 = 7, never overturned");
        assertEquals(7, batch.getQuantityReceived());
        assertEquals(UNIT_VALUE * 7, batch.getAllocatedTotal().paise(), "pinned total tracks the addition");
    }

    @Test
    @DisplayName("A later pricing that adds nothing leaves stock untouched")
    void laterPricingAddingNothingMovesNothing() {
        UUID lot = importLot("KADAI", 8);
        count(lot, "KADAI", 8);
        Batch batch = batchFor(lot, "KADAI");

        counting.addToInHand(batch, 0, AT.plusSeconds(60)); // fixing only price/MRP

        assertEquals(8, stock.onHandForBatch(batch.getId()));
        assertEquals(UNIT_VALUE * 8, batch.getAllocatedTotal().paise());
    }
}
