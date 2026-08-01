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
import com.bahikhaata.backend.inventory.FifoConsumer;
import com.bahikhaata.contracts.CartLineView;
import com.bahikhaata.contracts.CartView;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.PaymentMethod;
import com.bahikhaata.contracts.SaleLineView;
import com.bahikhaata.contracts.SaleView;
import java.time.Instant;
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
    private final SaleRepository sales;
    private final SaleLineRepository saleLines;
    private final FifoConsumer fifo;

    Checkout(
            CartRepository carts,
            CartLineRepository lines,
            BarcodeRepository barcodes,
            BatchRepository batches,
            SaleRepository sales,
            SaleLineRepository saleLines,
            FifoConsumer fifo) {
        this.carts = carts;
        this.lines = lines;
        this.barcodes = barcodes;
        this.batches = batches;
        this.sales = sales;
        this.saleLines = saleLines;
        this.fifo = fifo;
    }

    /**
     * Completes a cart into a persisted, immutable sale in one transaction: snapshots each line,
     * assigns the next bill number, records the payment method, and writes the {@code SALE} ledger
     * movements that decrement stock (FIFO, allowing negatives — see {@link FifoConsumer}). The
     * cart is marked paid so it cannot be completed again. The bill is a render of the returned sale
     * (printed by the caller, after this commits — a print failure never undoes the sale).
     *
     * <p>Tax is zero: the shop bills as a composition Bill of Supply and collects no tax, so the
     * total is simply the sum of the line prices — what the customer pays.
     */
    @Transactional
    public Sale complete(UUID cartId, PaymentMethod paymentMethod, String operatorName) {
        Cart cart = openCart(cartId); // rejects a cart already paid/abandoned — completion is once
        List<CartLine> cartLines = lines.findByCartIdOrderByCreatedAt(cartId);
        if (cartLines.isEmpty()) {
            throw new IllegalArgumentException("Cannot complete an empty cart.");
        }

        long billNo = sales.findTopByOrderByBillNoDesc().map(Sale::getBillNo).orElse(0L) + 1;
        Money subtotal = Money.ZERO;
        Money saving = Money.ZERO;
        for (CartLine line : cartLines) {
            subtotal = subtotal.plus(line.lineTotal());
            saving = saving.plus(line.saving());
        }
        Sale sale = sales.save(
                new Sale(billNo, paymentMethod.name(), subtotal, saving, Money.ZERO, subtotal,
                        operatorName));

        Instant now = Instant.now();
        for (CartLine line : cartLines) {
            Product product = line.getProduct();
            saleLines.save(new SaleLine(
                    sale.getId(), product.getId(), product.getName(), asinOf(product),
                    line.getMrp(), line.getUnitPrice(), line.getQuantity(),
                    line.lineTotal(), line.saving()));
            // Decrement stock through the ledger — FIFO for cost, never refused, may go negative.
            fifo.consumeForSale(product.getId(), line.getQuantity(), now);
        }
        cart.markPaid();
        return sale;
    }

    /** A completed sale as the till and sales screen show it, flagging whether its bill printed. */
    @Transactional(readOnly = true)
    public SaleView toView(Sale sale, boolean printFailed) {
        List<SaleLineView> lineViews =
                saleLines.findBySaleIdOrderByCreatedAtAsc(sale.getId()).stream()
                        .map(sl -> new SaleLineView(
                                sl.getProductId(), sl.getName(), sl.getBarcode(),
                                sl.getMrp().paise(), sl.getUnitPrice().paise(), sl.getQuantity(),
                                sl.getLineTotal().paise(), sl.getSaving().paise()))
                        .toList();
        return new SaleView(
                sale.getId(), sale.getBillNo(), sale.formattedBillNo(),
                PaymentMethod.valueOf(sale.getPaymentMethod()),
                sale.getSubtotal().paise(), sale.getSaving().paise(), sale.getTax().paise(),
                sale.getTotal().paise(), sale.getOperatorName(), sale.getCreatedAt(),
                lineViews, printFailed);
    }

    @Transactional
    public CartView open() {
        return view(carts.save(new Cart()).getId());
    }

    /**
     * Adds what was scanned, or raises its quantity if already on the cart.
     *
     * <p>The one thing a sale needs is a price — that is set at pricing, where the barcode is also
     * mapped, so a priced product is on the shelf. An MRP is not required: when present it drives
     * the saving shown against it, and when absent the item simply sells at its price with no
     * saving. Printing the label is a separate step (tracked by the product's label-printed flag)
     * and does not gate a sale either, so a failed or late print never strands sellable stock.
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
        // Optional — not every product carries an MRP. With one, the line strikes it and shows the
        // saving; without one, the line's MRP is just the price, so the saving is zero and nothing
        // is struck. The stored line still needs a non-null figure, so the price stands in.
        Money mrp = mrpForSale(product);
        Money lineMrp = mrp != null ? mrp : price;

        lines.findByCartIdAndProductId(cartId, product.getId())
                .ifPresentOrElse(
                        CartLine::addOne,
                        () -> lines.save(new CartLine(cart, product, price, lineMrp, 1)));
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
        // Shared with the shelf label (Money.percentOffTo) so the counter and the sticker agree.
        int percent = line.getMrp().percentOffTo(line.getUnitPrice());
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
    /** The MRP to strike on the receipt: the confirmed MRP from the product's stock, newest first,
     * or null when none was recorded. Null does not block a sale — it just means no saving to show. */
    private Money mrpForSale(Product product) {
        for (Batch batch : batches.findByProductIdNewestFirst(product.getId())) {
            if (batch.getMrp() != null) {
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
