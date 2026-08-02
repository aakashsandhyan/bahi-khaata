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

import com.bahikhaata.backend.catalog.Barcode;
import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.catalog.BarcodeResolver;
import com.bahikhaata.backend.catalog.InternalBarcodeGenerator;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.backend.inventory.CategoryCatalog;
import com.bahikhaata.backend.inventory.ExpectedLine;
import com.bahikhaata.backend.inventory.ExpectedLineRepository;
import com.bahikhaata.backend.inventory.GoodsInCounting;
import com.bahikhaata.backend.inventory.Lot;
import com.bahikhaata.backend.inventory.LotCategoryResolver;
import com.bahikhaata.backend.inventory.LotRepository;
import com.bahikhaata.backend.shelf.ProductPricing;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.LotState;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.Origin;
import com.bahikhaata.contracts.PriceExistingRequest;
import com.bahikhaata.contracts.PriceManualRequest;
import com.bahikhaata.contracts.PriceSuggestion;
import com.bahikhaata.contracts.ScannedItem;
import com.bahikhaata.contracts.ShelfLot;
import com.bahikhaata.contracts.ShelfPricedProduct;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The lot-first pricing workbench: turn received stock into priced, barcoded, sellable shelf
 * products. Reuses the margin machinery ({@link TargetMargins}, {@link Margins}) for suggestions,
 * the catalog's barcode generation for the shelf identity, and — for stock keyed in by hand — the
 * inventory receipt path, so the ledger rule lives in one place.
 *
 * <p>Two save paths, split by whether an item can be scanned to its record (see the design):
 * {@link #saveExisting} prices a scanned already-counted product and writes no stock; {@link
 * #saveManual} materialises hand-keyed stock into a batch and a receipt. MRP entered here is
 * recorded confirmed, so the label may show a real struck MRP.
 */
@Service
public class ShelfPricing {

    private final LotRepository lots;
    private final BatchRepository batches;
    private final ProductRepository products;
    private final BarcodeRepository barcodes;
    private final BarcodeResolver barcodeResolver;
    private final InternalBarcodeGenerator barcodeGenerator;
    private final GoodsInCounting goodsIn;
    private final TargetMargins targetMargins;
    private final ProductPricing productPricing;
    private final LotCategoryResolver lotCategories;
    private final CategoryCatalog categoryCatalog;
    private final ExpectedLineRepository expectedLines;

    public ShelfPricing(
            LotRepository lots,
            BatchRepository batches,
            ProductRepository products,
            BarcodeRepository barcodes,
            BarcodeResolver barcodeResolver,
            InternalBarcodeGenerator barcodeGenerator,
            GoodsInCounting goodsIn,
            TargetMargins targetMargins,
            ProductPricing productPricing,
            LotCategoryResolver lotCategories,
            CategoryCatalog categoryCatalog,
            ExpectedLineRepository expectedLines) {
        this.lots = lots;
        this.batches = batches;
        this.products = products;
        this.barcodes = barcodes;
        this.barcodeResolver = barcodeResolver;
        this.barcodeGenerator = barcodeGenerator;
        this.goodsIn = goodsIn;
        this.targetMargins = targetMargins;
        this.productPricing = productPricing;
        this.lotCategories = lotCategories;
        this.categoryCatalog = categoryCatalog;
        this.expectedLines = expectedLines;
    }

    /**
     * The lots the workbench can be scoped to, newest received first. A lot is listed when it is
     * still OPEN — including a mixed lot that was never counted, so it can be hand-priced into — or
     * when it holds counted stock still to be priced, whatever its state (a closed lot lingers only
     * while it has unpriced counted stock). A closed, fully-priced lot drops off; an open lot stays
     * until it is closed, since more stock can still be hand-priced into it.
     */
    @Transactional(readOnly = true)
    public List<ShelfLot> lots() {
        Map<UUID, String> categoryByLot = lotCategories.categoryByLot();
        Map<UUID, Lot> byId = new LinkedHashMap<>();
        for (Lot l : lots.findByStateOrderByReceivedOnDesc(LotState.OPEN)) {
            byId.put(l.getId(), l);
        }
        for (Lot l : lots.findAllById(batches.lotIdsWithUnpricedStock())) {
            byId.putIfAbsent(l.getId(), l);
        }
        return byId.values().stream()
                .sorted(Comparator.comparing(
                        Lot::getReceivedOn, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(l -> new ShelfLot(
                        l.getId(), l.getSupplier(), l.getReceivedOn(), categoryByLot.get(l.getId())))
                .toList();
    }

    /**
     * The scanner path: a scanned code (LSN/ASIN) is resolved to its product, and its batch in the
     * given lot is opened for pricing. Empty when the code is unknown or the product has no batch
     * in this lot — the item does not belong to the lot being priced, and it must be keyed in by
     * hand instead.
     */
    @Transactional(readOnly = true)
    public Optional<ScannedItem> resolveScanned(UUID lotId, String code) {
        return barcodeResolver
                .resolve(code)
                .flatMap(product -> batchInLot(lotId, product.getId())
                        .map(batch -> toScannedItem(product, batch)));
    }

    /**
     * TRANSIENT — direct scan, no lot chosen first. Resolves a scanned code to its product and any
     * batch it was counted into, in whatever lot, so already-manifested, already-scanned stock can
     * be priced in one scan. Empty when the code is unknown or the product was never counted. This
     * is a shortcut expected to be removed later; keep it self-contained.
     */
    @Transactional(readOnly = true)
    public Optional<ScannedItem> resolveScannedAnywhere(String code) {
        return barcodeResolver
                .resolve(code)
                .flatMap(product -> batchAnyFor(product.getId())
                        .map(batch -> toScannedItem(product, batch)));
    }

    /** Any batch the product was counted into, preferring good stock still on hand. */
    private Optional<Batch> batchAnyFor(UUID productId) {
        List<Batch> found = batches.findByProductId(productId);
        return found.stream()
                .filter(b -> b.getQuantityReceived() > 0
                        && b.getCondition() == com.bahikhaata.contracts.StockCondition.GOOD)
                .findFirst()
                .or(() -> found.stream().findFirst());
    }

    /**
     * The category choices for the dropdown: the distinct categories of products already in the lot,
     * or — when the lot holds none yet, as an uncounted mixed lot does — the shop's full category
     * list, so a hand-added item can still be classified.
     */
    @Transactional(readOnly = true)
    public List<String> categoriesForLot(UUID lotId) {
        List<String> fromBatches = batches.findByLotId(lotId).stream()
                .map(b -> b.getProduct().getCategory().code())
                .distinct()
                .toList();
        return fromBatches.isEmpty() ? categoryCatalog.allCodes() : fromBatches;
    }

    /** The shop's whole category list, for pickers that let an item be classified as anything. */
    @Transactional(readOnly = true)
    public List<String> allCategoryCodes() {
        return categoryCatalog.allCodes();
    }

    /**
     * A suggested selling price from a category's target margin against a unit cost. Only
     * meaningful for costed stock; the caller does not ask for uncosted stock, which is hand-priced.
     */
    @Transactional(readOnly = true)
    public PriceSuggestion suggestPrice(long unitCostPaise, String categoryCode, Integer customMargin) {
        int margin = targetMargins.resolve(Category.of(categoryCode), customMargin);
        Money price = Margins.priceForTargetMargin(Money.ofPaise(unitCostPaise), margin);
        return new PriceSuggestion(margin, price.paise());
    }

    /**
     * Prices a scanned, already-counted product: sets category and selling price, confirms the
     * batch MRP if one is given, and mints the BBZ if the product has none. Reconciles the in-hand
     * count taken at pricing — the count of record — into stock: on a first pricing the quantity is
     * the true total and overwrites; on a later one it is pieces found and is added; absent leaves
     * stock be (so a re-price that only fixes a figure moves nothing).
     */
    @Transactional
    public ShelfPricedProduct saveExisting(PriceExistingRequest req) {
        Product product = products.findById(req.productId())
                .orElseThrow(() -> new IllegalArgumentException("no such product: " + req.productId()));
        // Read before we set the price: no price yet means this is the first pricing.
        boolean firstPricing = product.getSellingPrice() == null;
        product.setCategory(Category.of(req.categoryCode()));
        if (req.name() != null && !req.name().isBlank()) {
            product.setName(req.name().trim());
        }

        // Touch the batch only when there is something to write to it — an MRP to confirm or an
        // in-hand count to reconcile — so a bare re-price needs no batch loaded.
        if (req.mrpPaise() != null || req.inHandQuantity() != null) {
            Batch batch = batches.findById(req.batchId())
                    .orElseThrow(() -> new IllegalArgumentException("no such batch: " + req.batchId()));
            // Confirm the MRP before pricing, so the price is checked against the figure the
            // operator just set, not a stale estimate.
            if (req.mrpPaise() != null) {
                batch.recordMrp(Money.ofPaise(req.mrpPaise()), false);
            }
            if (req.inHandQuantity() != null) {
                // A first pricing, or the reviewer's count of record, is the true total and
                // overwrites; a later pricing from the workbench adds the extra pieces found.
                if (req.setInHandAsTotal() || firstPricing) {
                    goodsIn.reconcileBatchTo(batch, req.inHandQuantity(), Instant.now());
                } else {
                    goodsIn.addToInHand(batch, req.inHandQuantity(), Instant.now());
                }
            }
            batches.save(batch);
        }

        // Route through the guarded setter: the MRP ceiling is enforced, and uncosted stock is
        // allowed (the workbench deliberately prices before a lot is costed — decision B-b).
        productPricing.setSellingPrice(product.getId(), Money.ofPaise(req.sellingPricePaise()), true);
        // labelPrintedAt is left as it was: it is the signal for whether this product has ever been
        // labelled, which the review queue uses to size a fresh entry — all of on-hand the first
        // time, only the newly-found units once it has printed before.
        String barcode = bbzFor(product);
        products.save(product);
        return new ShelfPricedProduct(
                product.getId(), barcode, product.getName(), req.sellingPricePaise(), req.mrpPaise());
    }

    /**
     * Prices stock keyed in by hand: creates the product, materialises the stock into a batch and
     * a receipt through the inventory path, sets category and selling price, records the MRP
     * confirmed, and mints the BBZ. This is the only pricing path that writes stock.
     */
    @Transactional
    public ShelfPricedProduct saveManual(PriceManualRequest req) {
        Lot lot = lots.findById(req.lotId())
                .orElseThrow(() -> new IllegalArgumentException("no such lot: " + req.lotId()));
        Category category = Category.of(req.categoryCode());
        Product product = products.save(new Product(req.name(), category, Map.of()));

        Money mrp = req.mrpPaise() != null ? Money.ofPaise(req.mrpPaise()) : null;
        goodsIn.receiveManual(
                lot,
                product,
                StockCondition.valueOf(req.condition()),
                req.quantity(),
                mrp,
                false,
                Instant.now());

        // Route through the guarded setter (MRP ceiling enforced; uncosted allowed — decision B-b).
        // The batch created above carries the MRP, so the ceiling is checked against it.
        productPricing.setSellingPrice(product.getId(), Money.ofPaise(req.sellingPricePaise()), true);
        String barcode = bbzFor(product);
        products.save(product);
        return new ShelfPricedProduct(
                product.getId(), barcode, product.getName(), req.sellingPricePaise(), req.mrpPaise());
    }

    /** The product's BBZ code, minting one if it has none — the shelf-scannable identity. */
    private String bbzFor(Product product) {
        return barcodes.findByProductId(product.getId()).stream()
                .filter(b -> b.getOrigin() == Origin.INTERNAL)
                .findFirst()
                .map(Barcode::getCode)
                .orElseGet(() -> barcodeGenerator.generateFor(product).getCode());
    }

    private Optional<Batch> batchInLot(UUID lotId, UUID productId) {
        return batches.findByLotIdAndProductId(lotId, productId).stream().findFirst();
    }

    private ScannedItem toScannedItem(Product product, Batch batch) {
        boolean costed = batch.isCosted();
        return new ScannedItem(
                product.getId(),
                product.getName(),
                product.getCategory().code(),
                batch.getId(),
                costed,
                costed ? batch.getAllocatedUnitCost().paise() : null,
                batch.getMrp() != null ? batch.getMrp().paise() : null,
                batch.isMrpEstimate(),
                batch.sellableQuantity(),
                product.getSellingPrice() != null ? product.getSellingPrice().paise() : null,
                expectedQuantityFor(batch));
    }

    /** The manifest's expected total for the batch's product in its lot, or null if unmanifested. */
    private Long expectedQuantityFor(Batch batch) {
        List<ExpectedLine> lines = expectedLines.findByLotIdAndProductIdOrderByCode(
                batch.getLot().getId(), batch.getProduct().getId());
        return lines.isEmpty() ? null : lines.stream().mapToLong(ExpectedLine::getQuantityExpected).sum();
    }
}
