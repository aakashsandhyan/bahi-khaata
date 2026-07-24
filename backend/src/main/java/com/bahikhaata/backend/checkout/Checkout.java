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
package com.bahikhaata.backend.checkout;

import com.bahikhaata.backend.catalog.Barcode;
import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.contracts.Origin;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.contracts.CartLineView;
import com.bahikhaata.contracts.CartView;
import com.bahikhaata.contracts.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ringing up a sale.
 *
 * <p>Scan a code, and if the goods are actually sellable — priced, and on the shelf with a label
 * bearing a printed MRP — a line joins the cart at that price. Scan it again and the quantity
 * rises. The saving against MRP is carried on every line, because that is what the shop is for.
 *
 * <p>Tax here is a <strong>placeholder</strong>. Real GST is per item by its HSN code, at rates a
 * CA supplies, and until those exist this total is indicative only and issues no invoice. The
 * cart is honest about that so nobody mistakes the figure for a receipt.
 */
@Service
public class Checkout {

    /**
     * A stand-in GST rate, applied flat, until real per-HSN rates arrive. Deliberately not a
     * quiet zero — the total should look roughly right on the till — but never treated as
     * correct, and never the basis of an invoice.
     */
    private static final int PLACEHOLDER_GST_PERCENT = 18;

    private final CartRepository carts;
    private final CartLineRepository lines;
    private final BarcodeRepository barcodes;
    private final BatchRepository batches;

    Checkout(
            CartRepository carts,
            CartLineRepository lines,
            BarcodeRepository barcodes,
            BatchRepository batches) {
        this.carts = carts;
        this.lines = lines;
        this.barcodes = barcodes;
        this.batches = batches;
    }

    @Transactional
    public CartView open() {
        return view(carts.save(new Cart()).getId());
    }

    /**
     * Adds what was scanned, or raises its quantity if already on the cart.
     *
     * <p>Refuses goods that are not sellable, saying which of price, MRP or label is missing — a
     * scan at the till is the last place to discover that quietly.
     */
    @Transactional
    public CartView scan(UUID cartId, String code) {
        Cart cart = openCart(cartId);
        Product product =
                barcodes.findByCode(code).map(Barcode::getProduct)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Nothing scans as " + code + "."));

        Money price = product.getSellingPrice();
        if (price == null) {
            throw new IllegalStateException(
                    "\"" + product.getName() + "\" has no price yet and cannot be sold.");
        }
        Money mrp = sellableMrp(product);
        if (mrp == null) {
            throw new IllegalStateException(
                    "\"" + product.getName() + "\" is not on the shelf yet — it needs a printed"
                            + " label before it can be sold.");
        }

        lines.findByCartIdAndProductId(cartId, product.getId())
                .ifPresentOrElse(
                        CartLine::addOne,
                        () -> lines.save(new CartLine(cart, product, price, mrp, 1)));
        return view(cartId);
    }

    @Transactional
    public CartView setQuantity(UUID cartId, UUID lineId, long quantity) {
        openCart(cartId);
        CartLine line = requireLine(cartId, lineId);
        if (quantity <= 0) {
            lines.delete(line);
        } else {
            line.setQuantity(quantity);
        }
        return view(cartId);
    }

    @Transactional
    public CartView removeLine(UUID cartId, UUID lineId) {
        openCart(cartId);
        lines.delete(requireLine(cartId, lineId));
        return view(cartId);
    }

    @Transactional
    public CartView clear(UUID cartId) {
        openCart(cartId);
        lines.deleteAll(lines.findByCartIdOrderByCreatedAt(cartId));
        return view(cartId);
    }

    @Transactional(readOnly = true)
    public CartView view(UUID cartId) {
        List<CartLineView> views =
                lines.findByCartIdOrderByCreatedAt(cartId).stream()
                        .map(this::lineView)
                        .toList();
        long subtotal = views.stream().mapToLong(CartLineView::lineTotalPaise).sum();
        long saving = views.stream().mapToLong(CartLineView::savingPaise).sum();
        long tax = subtotal * PLACEHOLDER_GST_PERCENT / 100;
        return new CartView(cartId, views, subtotal, tax, subtotal + tax, saving, true);
    }

    private CartLineView lineView(CartLine line) {
        long total = line.lineTotal().paise();
        long saving = line.saving().paise();
        long mrp = line.getMrp().paise();
        int percent =
                mrp == 0
                        ? 0
                        : BigDecimal.valueOf(
                                        (mrp - line.getUnitPrice().paise()) * 100L)
                                .divide(BigDecimal.valueOf(mrp), 0, RoundingMode.HALF_UP)
                                .intValue();
        return new CartLineView(
                line.getId(),
                line.getProduct().getId(),
                line.getProduct().getName(),
                asinOf(line.getProduct()),
                mrp,
                line.getUnitPrice().paise(),
                line.getQuantity(),
                total,
                saving,
                percent);
    }

    /** The product's marketplace reference (ASIN), so the counter can confirm the right item. */
    private String asinOf(Product product) {
        return barcodes.findByProductId(product.getId()).stream()
                .filter(b -> b.getOrigin() == Origin.MARKETPLACE)
                .map(Barcode::getCode)
                .findFirst()
                .orElse(null);
    }

    /** The printed MRP the goods on the shelf carry: the newest labelled batch with one. */
    private Money sellableMrp(Product product) {
        for (Batch batch : batches.findByProductIdNewestFirst(product.getId())) {
            if (batch.isLabelled() && batch.getMrp() != null) {
                return batch.getMrp();
            }
        }
        return null;
    }

    private Cart openCart(UUID cartId) {
        Cart cart = carts.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("no such cart: " + cartId));
        if (!cart.isOpen()) {
            throw new IllegalStateException("this sale is already " + cart.getState().toLowerCase());
        }
        return cart;
    }

    private CartLine requireLine(UUID cartId, UUID lineId) {
        CartLine line = lines.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("no such line: " + lineId));
        if (!line.getCart().getId().equals(cartId)) {
            throw new IllegalArgumentException("that line is not on this cart");
        }
        return line;
    }
}
