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
package com.bahikhaata.backend.pricing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.bahikhaata.backend.catalog.Barcode;
import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.catalog.BarcodeResolver;
import com.bahikhaata.backend.catalog.InternalBarcodeGenerator;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.backend.inventory.GoodsInCounting;
import com.bahikhaata.backend.inventory.Lot;
import com.bahikhaata.backend.inventory.LotRepository;
import com.bahikhaata.backend.shelf.ProductPricing;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.LotState;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.Origin;
import com.bahikhaata.contracts.PriceExistingRequest;
import com.bahikhaata.contracts.PriceManualRequest;
import com.bahikhaata.contracts.PriceSuggestion;
import com.bahikhaata.contracts.ShelfPricedProduct;
import com.bahikhaata.contracts.StockCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShelfPricingTest {

    @Mock private LotRepository lots;
    @Mock private BatchRepository batches;
    @Mock private ProductRepository products;
    @Mock private BarcodeRepository barcodes;
    @Mock private BarcodeResolver barcodeResolver;
    @Mock private InternalBarcodeGenerator barcodeGenerator;
    @Mock private GoodsInCounting goodsIn;
    @Mock private TargetMargins targetMargins;
    @Mock private ProductPricing productPricing;
    @Mock private com.bahikhaata.backend.inventory.LotCategoryResolver lotCategories;
    @Mock private com.bahikhaata.backend.inventory.CategoryCatalog categoryCatalog;
    @Mock private com.bahikhaata.backend.inventory.ExpectedLineRepository expectedLines;

    private ShelfPricing shelfPricing() {
        return new ShelfPricing(
                lots, batches, products, barcodes, barcodeResolver, barcodeGenerator, goodsIn,
                targetMargins, productPricing, lotCategories, categoryCatalog, expectedLines);
    }

    private Lot openLot(UUID id, String supplier, java.time.LocalDate receivedOn) {
        Lot lot = mock(Lot.class);
        when(lot.getId()).thenReturn(id);
        when(lot.getSupplier()).thenReturn(supplier);
        when(lot.getReceivedOn()).thenReturn(receivedOn);
        return lot;
    }

    private void stubMintsBbz(String code) {
        Barcode bbz = mock(Barcode.class);
        when(bbz.getCode()).thenReturn(code);
        when(barcodeGenerator.generateFor(any())).thenReturn(bbz);
    }

    @Test
    void savingAScannedExistingProductWritesNoStock() {
        Product product = new Product("Cooker", Category.of("KITCHEN"), Map.of());
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        when(barcodes.findByProductId(product.getId())).thenReturn(List.of());
        stubMintsBbz("BBZ-100500");

        ShelfPricedProduct result = shelfPricing().saveExisting(new PriceExistingRequest(
                product.getId(), UUID.randomUUID(), "APPLIANCE", 44900L, null, null, null, false, null));

        // Price is set through the guarded setter, allowing uncosted stock (decision B-b).
        verify(productPricing).setSellingPrice(product.getId(), Money.ofPaise(44900L), true);
        assertEquals("APPLIANCE", product.getCategory().code());
        assertEquals("BBZ-100500", result.barcode());
        // The stock was already received at counting — pricing writes no ledger movement.
        verifyNoInteractions(goodsIn);
    }

    @Test
    void savingAnExistingProductConfirmsTheBatchMrp() {
        Product product = new Product("Cooker", Category.of("KITCHEN"), Map.of());
        UUID batchId = UUID.randomUUID();
        Batch batch = mock(Batch.class);
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        when(batches.findById(batchId)).thenReturn(Optional.of(batch));
        when(barcodes.findByProductId(product.getId())).thenReturn(List.of());
        stubMintsBbz("BBZ-1");

        shelfPricing().saveExisting(new PriceExistingRequest(
                product.getId(), batchId, "KITCHEN", 44900L, 149900L, null, null, false, null));

        // Confirmed (non-estimate), so the label may strike it.
        verify(batch).recordMrp(Money.ofPaise(149900L), false);
    }

    @Test
    void reviewerEditOverwritesTheCountAndRenamesEvenWhenAlreadyPriced() {
        Product product = new Product("Messy manifest name", Category.of("KITCHEN"), Map.of());
        product.setSellingPrice(Money.ofRupees(100)); // already priced → normally a later add
        UUID batchId = UUID.randomUUID();
        Batch batch = mock(Batch.class);
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        when(batches.findById(batchId)).thenReturn(Optional.of(batch));
        when(barcodes.findByProductId(product.getId())).thenReturn(List.of());
        stubMintsBbz("BBZ-9");

        shelfPricing().saveExisting(new PriceExistingRequest(
                product.getId(), batchId, "APPLIANCE", 40000L, null, 3L, "Clean Name", true, null));

        // setInHandAsTotal overrides the first-vs-later rule: the count of record overwrites, never adds.
        verify(goodsIn).reconcileBatchTo(eq(batch), eq(3L), any());
        verify(goodsIn, never()).addToInHand(any(), org.mockito.ArgumentMatchers.anyLong(), any());
        assertEquals("Clean Name", product.getName());
        assertEquals("APPLIANCE", product.getCategory().code());
    }

    @Test
    void anExistingBbzIsKeptRatherThanMinted() {
        Product product = new Product("Cooker", Category.of("KITCHEN"), Map.of());
        Barcode existing = mock(Barcode.class);
        when(existing.getOrigin()).thenReturn(Origin.INTERNAL);
        when(existing.getCode()).thenReturn("BBZ-ALREADY");
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        when(barcodes.findByProductId(product.getId())).thenReturn(List.of(existing));

        ShelfPricedProduct result = shelfPricing().saveExisting(new PriceExistingRequest(
                product.getId(), UUID.randomUUID(), "KITCHEN", 44900L, null, null, null, false, null));

        assertEquals("BBZ-ALREADY", result.barcode());
        verify(barcodeGenerator, never()).generateFor(any());
    }

    @Test
    void savingAHandKeyedProductMaterialisesStockThroughInventory() {
        Lot lot = mock(Lot.class);
        UUID lotId = UUID.randomUUID();
        when(lots.findById(lotId)).thenReturn(Optional.of(lot));
        when(products.save(any())).thenAnswer(i -> i.getArgument(0));
        when(barcodes.findByProductId(any())).thenReturn(List.of());
        stubMintsBbz("BBZ-100600");

        ShelfPricedProduct result = shelfPricing().saveManual(new PriceManualRequest(
                lotId, "Mystery Item", "KITCHEN", "GOOD", 5L, 9900L, null, null));

        assertEquals("BBZ-100600", result.barcode());
        assertEquals(9900L, result.sellingPricePaise());
        // Stock enters through the inventory receipt path, with the captured quantity and condition.
        verify(goodsIn).receiveManual(eq(lot), any(Product.class), eq(StockCondition.GOOD), eq(5L),
                any(), anyBoolean(), any());
        // And the price is set through the guarded setter.
        verify(productPricing).setSellingPrice(any(), eq(Money.ofPaise(9900L)), eq(true));
    }

    @Test
    void listsLotsThatHaveUnpricedCountedStock() {
        java.util.UUID lotId = java.util.UUID.randomUUID();
        com.bahikhaata.backend.inventory.Lot lot = mock(com.bahikhaata.backend.inventory.Lot.class);
        when(lot.getId()).thenReturn(lotId);
        when(lot.getSupplier()).thenReturn("Acme");
        when(lot.getReceivedOn()).thenReturn(java.time.LocalDate.of(2026, 7, 1));
        when(batches.lotIdsWithUnpricedStock()).thenReturn(java.util.List.of(lotId));
        when(lots.findAllById(java.util.List.of(lotId))).thenReturn(java.util.List.of(lot));
        when(lotCategories.categoryByLot()).thenReturn(Map.of(lotId, "KITCHEN"));

        var result = shelfPricing().lots();

        assertEquals(1, result.size());
        assertEquals("Acme", result.get(0).supplier());
        assertEquals("KITCHEN", result.get(0).categoryCode());
    }

    @Test
    void listsAnOpenLotThatHasNoCountedStockYet() {
        UUID lotId = UUID.randomUUID();
        Lot lot = openLot(lotId, "Mixed Bulk", java.time.LocalDate.of(2026, 8, 1));
        when(lots.findByStateOrderByReceivedOnDesc(LotState.OPEN)).thenReturn(List.of(lot));

        var result = shelfPricing().lots();

        // A just-added, uncounted lot appears so it can be hand-priced into.
        assertEquals(1, result.size());
        assertEquals("Mixed Bulk", result.get(0).supplier());
    }

    @Test
    void doesNotListAClosedFullyPricedLot() {
        // Open set empty, and no lot has unpriced counted stock — a closed, fully-priced lot is in
        // neither, so nothing is listed.
        assertTrue(shelfPricing().lots().isEmpty());
    }

    @Test
    void listsALotInBothSetsOnlyOnce() {
        UUID lotId = UUID.randomUUID();
        Lot lot = openLot(lotId, "Acme", java.time.LocalDate.of(2026, 7, 1));
        when(lots.findByStateOrderByReceivedOnDesc(LotState.OPEN)).thenReturn(List.of(lot));
        when(batches.lotIdsWithUnpricedStock()).thenReturn(List.of(lotId));
        when(lots.findAllById(List.of(lotId))).thenReturn(List.of(lot));

        var result = shelfPricing().lots();

        assertEquals(1, result.size());
    }

    @Test
    void categoriesForLotFallsBackToTheFullListWhenTheLotHasNoBatches() {
        UUID lotId = UUID.randomUUID();
        when(batches.findByLotId(lotId)).thenReturn(List.of());
        when(categoryCatalog.allCodes()).thenReturn(List.of("ELECTRONICS", "KITCHEN"));

        assertEquals(List.of("ELECTRONICS", "KITCHEN"), shelfPricing().categoriesForLot(lotId));
    }

    @Test
    void categoriesForLotUsesTheLotsOwnCategoriesWhenItHasBatches() {
        UUID lotId = UUID.randomUUID();
        Product product = new Product("Cooker", Category.of("KITCHEN"), Map.of());
        Batch batch = mock(Batch.class);
        when(batch.getProduct()).thenReturn(product);
        when(batches.findByLotId(lotId)).thenReturn(List.of(batch));

        assertEquals(List.of("KITCHEN"), shelfPricing().categoriesForLot(lotId));
        // The full-list fallback is not consulted when the lot names its own categories.
        verifyNoInteractions(categoryCatalog);
    }

    @Test
    void directScanResolvesACountedProductInAnyLot() {
        Product product = new Product("Cooker", Category.of("KITCHEN"), Map.of());
        Batch batch = mock(Batch.class);
        com.bahikhaata.backend.inventory.Lot lot = mock(com.bahikhaata.backend.inventory.Lot.class);
        UUID lotId = UUID.randomUUID();
        when(lot.getId()).thenReturn(lotId);
        when(batch.getId()).thenReturn(UUID.randomUUID());
        when(batch.getQuantityReceived()).thenReturn(3L);
        when(batch.getCondition()).thenReturn(StockCondition.GOOD);
        when(batch.isCosted()).thenReturn(true);
        when(batch.getAllocatedUnitCost()).thenReturn(Money.ofPaise(35_759L));
        when(batch.getMrp()).thenReturn(null);
        when(batch.isMrpEstimate()).thenReturn(false);
        when(batch.sellableQuantity()).thenReturn(12L);
        when(batch.getLot()).thenReturn(lot);
        when(batch.getProduct()).thenReturn(product);
        when(barcodeResolver.resolve("B08RWJ5MGW")).thenReturn(Optional.of(product));
        when(batches.findByProductId(product.getId())).thenReturn(List.of(batch));
        // Manifested 15, so the workbench can show it against the in-hand count.
        com.bahikhaata.backend.inventory.ExpectedLine line =
                mock(com.bahikhaata.backend.inventory.ExpectedLine.class);
        when(line.getQuantityExpected()).thenReturn(15L);
        when(expectedLines.findByLotIdAndProductIdOrderByCode(lotId, product.getId()))
                .thenReturn(List.of(line));

        var found = shelfPricing().resolveScannedAnywhere("B08RWJ5MGW");

        assertTrue(found.isPresent());
        assertEquals("KITCHEN", found.get().categoryCode());
        assertTrue(found.get().costed());
        assertEquals(35_759L, found.get().unitCostPaise());
        assertEquals(12L, found.get().quantity(), "the counted stock, so pricing can print one label each");
        assertEquals(15L, found.get().expectedQuantity(), "the manifest total, for reference");
    }

    @Test
    void directScanMissesAnUncountedCode() {
        when(barcodeResolver.resolve("NOPE")).thenReturn(Optional.empty());
        assertTrue(shelfPricing().resolveScannedAnywhere("NOPE").isEmpty());
    }

    @Test
    void suggestsPriceFromCategoryMarginAndUnitCost() {
        when(targetMargins.resolve(Category.of("KITCHEN"), null)).thenReturn(40);

        PriceSuggestion suggestion = shelfPricing().suggestPrice(30000L, "KITCHEN", null);

        assertEquals(40, suggestion.marginPercent());
        // priceForTargetMargin(₹300, 40%) = 300 / (1 - 0.40) = ₹500.
        assertEquals(50000L, suggestion.suggestedPricePaise());
    }
}
