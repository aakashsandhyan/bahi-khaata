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
import com.bahikhaata.contracts.Category;
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

    private ShelfPricing shelfPricing() {
        return new ShelfPricing(
                lots, batches, products, barcodes, barcodeResolver, barcodeGenerator, goodsIn, targetMargins);
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
                product.getId(), UUID.randomUUID(), "APPLIANCE", 44900L, null));

        assertEquals(Money.ofPaise(44900L), product.getSellingPrice());
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
                product.getId(), batchId, "KITCHEN", 44900L, 149900L));

        // Confirmed (non-estimate), so the label may strike it.
        verify(batch).recordMrp(Money.ofPaise(149900L), false);
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
                product.getId(), UUID.randomUUID(), "KITCHEN", 44900L, null));

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
                lotId, "Mystery Item", "KITCHEN", "GOOD", 5L, 9900L, null));

        assertEquals("BBZ-100600", result.barcode());
        assertEquals(9900L, result.sellingPricePaise());
        // Stock enters through the inventory receipt path, with the captured quantity and condition.
        verify(goodsIn).receiveManual(eq(lot), any(Product.class), eq(StockCondition.GOOD), eq(5L),
                any(), anyBoolean(), any());
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
