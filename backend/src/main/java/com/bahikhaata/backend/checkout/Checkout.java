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
package com.bahikhaata.backend.checkout;

import com.bahikhaata.backend.catalog.Barcode;
import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.contracts.Origin;
import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.backend.inventory.FifoConsumer;
import com.bahikhaata.backend.customer.CustomerRepository;
import com.bahikhaata.backend.inventory.LotRepository;
import com.bahikhaata.contracts.CartLineView;
import com.bahikhaata.contracts.CartView;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.PaymentMethod;
import com.bahikhaata.contracts.SaleLineView;
import com.bahikhaata.contracts.SaleSummary;
import com.bahikhaata.contracts.SaleView;
import com.bahikhaata.backend.tax.GstMath;
import com.bahikhaata.backend.tax.GstRates;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ringing up a sale.
 *
 * <p>Scan a code, and if the goods are actually sellable — priced, and on the shelf with a label
 * bearing a printed MRP — a line joins the cart at that price. Scan it again and the quantity
 * rises. The saving against MRP is carried on every line, because that is what the shop is for.
 *
 * <p>GST is <strong>inclusive</strong>: the selling price already contains the tax (Indian MRP is
 * tax-inclusive by law), so it is extracted from the price, never added on top — the total the
 * customer pays is the sum of the line prices. The rate comes from each product's GST sub-category
 * ({@link GstRates}), and the extracted CGST/SGST is frozen onto the sale at completion.
 */
@Service
public class Checkout {

    private final CartRepository carts;
    private final CartLineRepository lines;
    private final BarcodeRepository barcodes;
    private final BatchRepository batches;
    private final SaleRepository sales;
    private final SaleLineRepository saleLines;
    private final FifoConsumer fifo;
    private final GstRates gstRates;
    private final LotRepository lotRepository;
    private final CustomerRepository customerRepository;

    Checkout(
            CartRepository carts,
            CartLineRepository lines,
            BarcodeRepository barcodes,
            BatchRepository batches,
            SaleRepository sales,
            SaleLineRepository saleLines,
            FifoConsumer fifo,
            GstRates gstRates,
            LotRepository lotRepository,
            CustomerRepository customerRepository) {
        this.carts = carts;
        this.lines = lines;
        this.barcodes = barcodes;
        this.batches = batches;
        this.sales = sales;
        this.saleLines = saleLines;
        this.fifo = fifo;
        this.gstRates = gstRates;
        this.lotRepository = lotRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * Completes a cart into a persisted, immutable sale in one transaction: snapshots each line,
     * assigns the next bill number, records the payment method, and writes the {@code SALE} ledger
     * movements that decrement stock (FIFO, allowing negatives — see {@link FifoConsumer}). The
     * cart is marked paid so it cannot be completed again. The bill is a render of the returned sale
     * (printed by the caller, after this commits — a print failure never undoes the sale).
     *
     * <p>GST is extracted inclusively from the line prices at each product's rate and frozen onto the
     * sale (tax, CGST, SGST, taxable value); the total stays the sum of the prices — the customer
     * pays the MRP-inclusive amount, never that plus a tax add-on.
     */
    @Transactional
    public Sale complete(UUID cartId, PaymentMethod paymentMethod, String operatorName) {
        return complete(cartId, paymentMethod, operatorName, null);
    }

    /**
     * As {@link #complete(UUID, PaymentMethod, String)}, additionally attaching the sale to a
     * register session. The id arrives pre-validated (the controller checks it is open) — this
     * class stays register-ignorant and just records the fact.
     */
    @Transactional
    public Sale complete(UUID cartId, PaymentMethod paymentMethod, String operatorName, UUID registerSessionId) {
        return complete(cartId, paymentMethod, operatorName, registerSessionId, null);
    }

    /**
     * As above, additionally recording the captured customer. Null is a walk-in. The id arrives
     * pre-validated at the API edge, like the session's — this class records facts, not policy.
     */
    @Transactional
    public Sale complete(
            UUID cartId, PaymentMethod paymentMethod, String operatorName,
            UUID registerSessionId, UUID customerId) {
        Cart cart = openCart(cartId); // rejects a cart already paid/abandoned — completion is once
        List<CartLine> cartLines = lines.findByCartIdOrderByCreatedAt(cartId);
        if (cartLines.isEmpty()) {
            throw new IllegalArgumentException("Cannot complete an empty cart.");
        }

        long billNo = sales.findTopByOrderByBillNoDesc().map(Sale::getBillNo).orElse(0L) + 1;
        Money subtotal = Money.ZERO;
        Money saving = Money.ZERO;
        List<GstMath.Line> gstLines = new ArrayList<>(cartLines.size());
        int[] lineBasisPoints = new int[cartLines.size()];
        for (int i = 0; i < cartLines.size(); i++) {
            CartLine line = cartLines.get(i);
            subtotal = subtotal.plus(line.lineTotal());
            saving = saving.plus(line.saving());
            int bp = gstRates.resolveBasisPoints(line.gstSubCategory());
            lineBasisPoints[i] = bp;
            gstLines.add(new GstMath.Line(line.lineTotal().paise(), bp));
        }
        // GST is extracted from the MRP-inclusive prices, never added: the total stays the subtotal.
        GstMath.Breakdown gst = GstMath.invoice(gstLines);
        Sale sale = new Sale(billNo, paymentMethod.name(), subtotal, saving,
                Money.ofPaise(gst.taxPaise()), Money.ofPaise(gst.cgstPaise()),
                Money.ofPaise(gst.sgstPaise()), Money.ofPaise(gst.taxablePaise()),
                subtotal, operatorName);
        sale.setRegisterSessionId(registerSessionId);
        // The cart's customer is the truth (a held cart kept its person); the request-level id
        // remains the fallback for callers that never attach to the cart (the classic till, APIs).
        sale.setCustomerId(cart.getCustomerId() != null ? cart.getCustomerId() : customerId);
        sale = sales.save(sale);

        Instant now = Instant.now();
        for (int i = 0; i < cartLines.size(); i++) {
            CartLine line = cartLines.get(i);
            Product product = line.getProduct();
            long lineTax = GstMath.lineTaxPaise(line.lineTotal().paise(), lineBasisPoints[i]);
            SaleLine saleLine = new SaleLine(
                    sale.getId(), product != null ? product.getId() : null, line.displayName(),
                    product != null ? asinOf(product) : null,
                    line.getMrp(), line.getUnitPrice(), line.getQuantity(),
                    line.lineTotal(), line.saving(), lineBasisPoints[i], Money.ofPaise(lineTax));
            saleLine.setLotId(line.getLotId());
            saleLines.save(saleLine);
            if (product != null) {
                // Decrement stock through the ledger — FIFO for cost, never refused, may go negative.
                fifo.consumeForSale(product.getId(), line.getQuantity(), now);
            }
            // A custom line writes no ledger movement: its stock was never in the system — that is
            // exactly why it was keyed by hand. Its lot reference is attribution, not consumption.
        }
        cart.markPaid();
        return sale;
    }

    /** Recent sales, newest first, for the sales screen — a summary row each, no lines loaded. */
    @Transactional(readOnly = true)
    public List<SaleSummary> recentSales(int limit) {
        return sales.findByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(this::toSummary)
                .toList();
    }

    /** One register session's bills, oldest first — the close-drawer review. */
    public List<SaleSummary> sessionSales(UUID registerSessionId) {
        return sales.findByRegisterSessionIdOrderByCreatedAtAsc(registerSessionId).stream()
                .map(this::toSummary)
                .toList();
    }

    private SaleSummary toSummary(Sale s) {
        return new SaleSummary(
                s.getId(), s.getBillNo(), s.formattedBillNo(), s.getTotal().paise(),
                PaymentMethod.valueOf(s.getPaymentMethod()), s.getCreatedAt(),
                saleLines.countBySaleId(s.getId()),
                saleCustomer(s).map(com.bahikhaata.backend.customer.Customer::getName).orElse(null));
    }

    private java.util.Optional<com.bahikhaata.backend.customer.Customer> saleCustomer(Sale s) {
        return java.util.Optional.ofNullable(s.getCustomerId()).flatMap(customerRepository::findById);
    }

    /** A single stored sale by its bill number, fully lined, for viewing or reprint. */
    @Transactional(readOnly = true)
    public SaleView saleByBillNo(long billNo) {
        return sales.findByBillNo(billNo)
                .map(s -> toView(s, false))
                .orElseThrow(() -> new IllegalArgumentException("No sale numbered " + billNo + "."));
    }

    /** Loads a stored sale by id — the reprint path re-renders from this, never from a live cart. */
    @Transactional(readOnly = true)
    public Sale requireSale(UUID saleId) {
        return sales.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("No such sale: " + saleId));
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
        var customer = saleCustomer(sale);
        return new SaleView(
                sale.getId(), sale.getBillNo(), sale.formattedBillNo(),
                PaymentMethod.valueOf(sale.getPaymentMethod()),
                sale.getSubtotal().paise(), sale.getSaving().paise(), sale.getTax().paise(),
                sale.getCgst().paise(), sale.getSgst().paise(), sale.getTaxable().paise(),
                sale.getTotal().paise(), sale.getOperatorName(), sale.getCreatedAt(),
                lineViews, printFailed,
                customer.map(com.bahikhaata.backend.customer.Customer::getName).orElse(null),
                customer.map(c -> com.bahikhaata.backend.customer.CustomerQueries.mask(c.getMobile()))
                        .orElse(null));
    }

    @Transactional
    public CartView open() {
        return open(null);
    }

    /** Opens a cart stamped with the register it belongs to — the carts panel's chip. */
    @Transactional
    public CartView open(String registerName) {
        Cart cart = new Cart();
        cart.setRegisterName(registerName);
        return view(carts.save(cart).getId());
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
        return addLine(cart, cartId, product);
    }

    /**
     * Adds a product picked by identity rather than by scan — the quick-picks grid, where the
     * operator taps a tile instead of holding a barcode. Same rules as a scan from here on.
     */
    @Transactional
    public CartView addProduct(UUID cartId, UUID productId) {
        Cart cart = openCart(cartId);
        Product product = barcodes.findByProductId(productId).stream()
                .findFirst()
                .map(Barcode::getProduct)
                .orElseThrow(() -> new IllegalArgumentException("No such product to add."));
        return addLine(cart, cartId, product);
    }

    /**
     * A manual-entry line: a thing sold with no product record — name and price keyed at the
     * counter. GST resolves from the chosen sub-category (or the global default). The lot is
     * optional attribution to the delivery it came from; no stock ledger entry is written, since
     * the stock was never in the system. MRP is optional — absent, the price stands in and the
     * saving is zero, same rule as a product without an MRP.
     */
    @Transactional
    public CartView addCustomLine(
            UUID cartId, String name, long pricePaise, Long mrpPaise, String subCategory, UUID lotId) {
        Cart cart = openCart(cartId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A manual entry needs a name.");
        }
        if (pricePaise <= 0) {
            throw new IllegalArgumentException("A manual entry needs a price above zero.");
        }
        if (mrpPaise != null && mrpPaise < pricePaise) {
            throw new IllegalArgumentException("MRP cannot be below the selling price.");
        }
        if (lotId != null && lotRepository.findById(lotId).isEmpty()) {
            throw new IllegalArgumentException("No such lot to attribute this entry to.");
        }
        Money price = Money.ofPaise(pricePaise);
        Money mrp = mrpPaise != null ? Money.ofPaise(mrpPaise) : price;
        lines.save(CartLine.custom(cart, name.trim(), price, mrp, subCategory, lotId));
        return view(cartId);
    }

    /** The one way a product enters a cart, however it was picked. */
    private CartView addLine(Cart cart, UUID cartId, Product product) {
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
        List<CartLine> cartLines = lines.findByCartIdOrderByCreatedAt(cartId);
        List<CartLineView> views = cartLines.stream().map(this::lineView).toList();
        long subtotal = views.stream().mapToLong(CartLineView::lineTotalPaise).sum();
        long saving = views.stream().mapToLong(CartLineView::savingPaise).sum();
        // GST is inclusive: the price already contains it, so it is extracted, never added. The
        // total the customer pays is the subtotal — the placeholder that added a flat % is gone.
        GstMath.Breakdown gst =
                GstMath.invoice(
                        cartLines.stream()
                                .map(l -> new GstMath.Line(
                                        l.lineTotal().paise(),
                                        gstRates.resolveBasisPoints(l.gstSubCategory())))
                                .toList());
        Cart cart = carts.findById(cartId).orElseThrow();
        return new CartView(cartId, views, subtotal, gst.taxPaise(), subtotal, saving, false,
                cart.getCustomerId(), customerNameOf(cart));
    }

    private CartLineView lineView(CartLine line) {
        long total = line.lineTotal().paise();
        long saving = line.saving().paise();
        long mrp = line.getMrp().paise();
        // Shared with the shelf label (Money.percentOffTo) so the counter and the sticker agree.
        int percent = line.getMrp().percentOffTo(line.getUnitPrice());
        Product product = line.getProduct();
        return new CartLineView(
                line.getId(),
                product != null ? product.getId() : null,
                line.displayName(),
                product != null ? asinOf(product) : null,
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
        // Every mutation passes through here, so this is the one place the touch discipline
        // lives: the carts panel's order and the sweep's clock stay honest for free.
        cart.touch();
        carts.save(cart);
        return cart;
    }

    /** The IST business day — a cart last touched before today is yesterday's, whatever the UTC clock says. */
    private static final java.time.ZoneId SHOP_ZONE = java.time.ZoneId.of("Asia/Kolkata");

    private boolean fromAPreviousDay(Cart cart) {
        return cart.getTouchedAt().atZone(SHOP_ZONE).toLocalDate()
                .isBefore(Instant.now().atZone(SHOP_ZONE).toLocalDate());
    }

    /** Sweeps one stale cart; true when it was abandoned. */
    private boolean sweepIfStale(Cart cart) {
        if (cart.isOpen() && fromAPreviousDay(cart)) {
            cart.markAbandoned();
            carts.save(cart);
            return true;
        }
        return false;
    }

    /**
     * A device restoring its remembered cart: the open cart's view, or empty when it is gone,
     * paid, abandoned — or was yesterday's, in which case this read is the sweep that takes it.
     */
    @Transactional
    public java.util.Optional<CartView> restore(UUID cartId) {
        return carts.findById(cartId)
                .filter(cart -> !sweepIfStale(cart))
                .filter(Cart::isOpen)
                .map(cart -> view(cartId));
    }

    /** The open carts for the panel, newest touch first — sweeping yesterday's on the way through. */
    @Transactional
    public List<com.bahikhaata.contracts.CartSummary> openCarts() {
        return carts.findByStateOrderByTouchedAtDesc("OPEN").stream()
                .filter(cart -> !sweepIfStale(cart))
                .map(this::summarize)
                .toList();
    }

    private com.bahikhaata.contracts.CartSummary summarize(Cart cart) {
        List<CartLine> cartLines = lines.findByCartIdOrderByCreatedAt(cart.getId());
        int items = (int) cartLines.stream().mapToLong(CartLine::getQuantity).sum();
        String summary = cartLines.isEmpty()
                ? "empty"
                : cartLines.size() == 1
                        ? cartLines.get(0).displayName()
                        : cartLines.get(0).displayName() + " + " + (cartLines.size() - 1) + " more";
        long total = cartLines.stream().mapToLong(l -> l.lineTotal().paise()).sum();
        return new com.bahikhaata.contracts.CartSummary(
                cart.getId(), customerNameOf(cart), items, summary, total,
                cart.getRegisterName(), cart.getTouchedAt());
    }

    /** Attaches (or with null, detaches) the cart's customer — a held cart keeps its person. */
    @Transactional
    public CartView attachCustomer(UUID cartId, UUID customerId) {
        Cart cart = openCart(cartId);
        if (customerId != null && customerRepository.findById(customerId).isEmpty()) {
            throw new IllegalArgumentException("No such customer.");
        }
        cart.setCustomerId(customerId);
        carts.save(cart);
        return view(cartId);
    }

    private String customerNameOf(Cart cart) {
        if (cart.getCustomerId() == null) {
            return null;
        }
        return customerRepository.findById(cart.getCustomerId())
                .map(com.bahikhaata.backend.customer.Customer::getName)
                .orElse(null);
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
