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

import com.bahikhaata.backend.persistence.InstantIso8601Converter;
import com.bahikhaata.backend.persistence.LocalDateIso8601Converter;
import com.bahikhaata.backend.persistence.MoneyConverter;
import com.bahikhaata.backend.persistence.UuidEntity;
import com.bahikhaata.contracts.Category;
import com.bahikhaata.contracts.Marketplace;
import com.bahikhaata.contracts.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A product in the catalogue.
 *
 * <p>Mutable — name, price, and attributes change over a product's life — so unlike ledger
 * and invoice rows it carries no immutability trigger and has an {@code updated_at}.
 *
 * <p>Selling price is nullable and is the product's alone: an unpriced product has a null
 * price (never zero), and receiving stock at any cost never changes it. Those rules are
 * enforced by the operations added in later tasks; this class holds the state they act on.
 *
 * <p>Column types are pinned to match the reviewed V3 migration under {@code ddl-auto=validate}:
 * text columns are declared {@code text} rather than the default {@code varchar(255)}, so a
 * product name is not silently capped at 255 characters and the migration stays idiomatic
 * SQLite. Instant/boolean/JSON types were confirmed against the dialect in task 2.1.
 */
@Entity
@Table(name = "product")
public class Product extends UuidEntity {

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    /**
     * The category's code, a foreign key into the category table. Stored as the code rather
     * than mapped as an association: a product's category is a label it carries, and loading
     * a whole row to read one string would be work for nothing.
     */
    @Column(name = "category", nullable = false, columnDefinition = "text")
    private String categoryCode;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "selling_price_paise")
    private Money sellingPrice;

    /**
     * What damaged units of this product sell for, or null if nobody has decided yet.
     *
     * <p>A second price rather than a price on the batch: "selling price belongs to the product,
     * never to a batch" is a settled decision that FIFO costing depends on, and it holds. The
     * product simply carries two prices now, one per condition.
     *
     * <p>Set separately and later. The person unpacking marks an item damaged and moves on;
     * what it is then worth is a judgement for someone who can see what it cost.
     */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "damaged_selling_price_paise")
    private Money damagedSellingPrice;

    @Column(name = "hsn_code", columnDefinition = "text")
    private String hsnCode;

    /**
     * What one unit last sold for on an online marketplace, or null where nobody has seen a
     * figure. An input to deciding a shelf price, never an authority for one.
     */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "online_price_paise")
    private Money onlinePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "online_price_source", columnDefinition = "text")
    private Marketplace onlinePriceSource;

    /**
     * When that price was seen. Stored because the number decays: a price observed a year ago
     * is not evidence today, and without its date nobody can tell how much to trust it.
     */
    @Convert(converter = LocalDateIso8601Converter.class)
    @Column(name = "online_price_observed_on", columnDefinition = "text")
    private LocalDate onlinePriceObservedOn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes")
    private Map<String, Object> attributes;

    @Column(name = "price_review_flagged", nullable = false)
    private boolean priceReviewFlagged;

    /**
     * When a price look-up last tried this product, or null if never. Recorded whether or not a
     * price was found, so a background fill asks each product once and never scrapes the same dead
     * end again. It bounds only the automatic pass — a person holding the pack still reads it, and
     * a deliberate one-item suggestion is free to ask again.
     */
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "mrp_lookup_attempted_at", columnDefinition = "text")
    private Instant mrpLookupAttemptedAt;

    /**
     * When a label for this product last printed, or null if never. Lets the bulk-print screen
     * find shelf products still awaiting a label; a reprint leaves it set.
     */
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "label_printed_at", columnDefinition = "text")
    private Instant labelPrintedAt;

    @CreationTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "text")
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantIso8601Converter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "text")
    private Instant updatedAt;

    /** For Hibernate. */
    protected Product() {}

    /**
     * Creates an unpriced product. Price is set later, deliberately: stock is routinely
     * received before anyone has decided what it is worth.
     */
    public Product(String name, Category category, Map<String, Object> attributes) {
        this(name, category, null, attributes);
    }

    /** As above, recording an HSN code where one is known. */
    public Product(
            String name, Category category, String hsnCode, Map<String, Object> attributes) {
        super(newId());
        this.name = Objects.requireNonNull(name, "name");
        this.categoryCode = Objects.requireNonNull(category, "category").code();
        this.hsnCode = hsnCode;
        this.attributes = attributes;
    }

    public String getName() {
        return name;
    }

    /**
     * Renames the product. The name is a label it carries — decided from the manifest and
     * correctable when a reviewer catches a wrong or messy one; nothing costing rides on it.
     */
    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public Category getCategory() {
        return Category.of(categoryCode);
    }

    /**
     * Sets the product's category. Its category is a label it carries, decided or corrected at
     * pricing; unlike selling price it has no costing invariant riding on it.
     */
    public void setCategory(Category category) {
        this.categoryCode = Objects.requireNonNull(category, "category").code();
    }

    /** The selling price, or null when the product is unpriced. Never zero for "no price". */
    public Money getSellingPrice() {
        return sellingPrice;
    }

    /**
     * Whether a selling price has been set. An unpriced product is a real, legitimate state —
     * stock received before anyone has decided what it is worth — not an error and not a
     * price of zero.
     *
     * <p>Being priced is necessary to sell but not sufficient: see {@link #isSellable()}.
     */
    public boolean isPriced() {
        return sellingPrice != null;
    }

    /**
     * Whether a price look-up has already tried this product. Once tried, a background fill leaves
     * it alone: the same source asked twice returns the same answer, so a second scrape only spends
     * the rate budget on a settled question.
     */
    public boolean isMrpLookupAttempted() {
        return mrpLookupAttemptedAt != null;
    }

    /** Records that a look-up tried this product, found or not, so no later run repeats it. */
    public void markMrpLookupAttempted(Instant at) {
        this.mrpLookupAttemptedAt = Objects.requireNonNull(at, "at");
    }

    /**
     * Clears the tried-mark, for a product a blocked source only appeared to try. A source that
     * refused to answer never really tested it, so it must be free to try again when the source is
     * healthy — otherwise one throttled afternoon would burn the catalogue to "tried, no price".
     */
    public void clearMrpLookupAttempted() {
        this.mrpLookupAttemptedAt = null;
    }

    /** Whether a label for this product has printed. */
    public boolean isLabelPrinted() {
        return labelPrintedAt != null;
    }

    /** When a label last printed, or null if never. */
    public Instant getLabelPrintedAt() {
        return labelPrintedAt;
    }

    /** Records that a label printed, at the given instant. A reprint simply re-stamps it. */
    public void markLabelPrinted(Instant at) {
        this.labelPrintedAt = Objects.requireNonNull(at, "at");
    }

    /**
     * Marks the product as needing a label again, so it re-enters the review screen's awaiting list.
     * A re-pricing — more stock found, a corrected price, MRP or name — means the printed labels no
     * longer match, so the product is sent back for the reviewer to send afresh.
     */
    public void clearLabelPrinted() {
        this.labelPrintedAt = null;
    }

    /**
     * Whether this product may be sold.
     *
     * <p>Requires a price and a recorded MRP. The MRP is not a formality: it is the printed
     * maximum retail price, selling above it is unlawful, and the label a customer reads
     * shows the saving against it. Without one there is no lawful ceiling to price beneath
     * and nothing to show a discount from, so the product stays off the floor however
     * certain anyone is about what it should cost.
     *
     * <p>MRP lives on the batch, since successive deliveries of the same product genuinely
     * arrive bearing different printed prices, so sellability is decided with the batch in
     * hand rather than from the product alone.
     */
    public boolean isSellable(Money recordedMrp) {
        return isPriced() && recordedMrp != null;
    }

    /**
     * Sets the selling price. This is the only thing that changes a product's price: no other
     * operation — not receiving stock at a new cost, not flagging for margin review — may
     * touch it. That is the invariant the whole no-auto-repricing model rests on.
     *
     * <p>The price must be a real, positive amount. A product cannot be un-priced by passing
     * null, and cannot be set to zero or a negative value.
     */
    public void setSellingPrice(Money price) {
        Objects.requireNonNull(price, "selling price (a product cannot be un-priced)");
        if (!price.isPositive()) {
            throw new IllegalArgumentException("selling price must be positive, was " + price);
        }
        this.sellingPrice = price;
    }

    /** What one unit last sold for online, or null if nobody has seen a figure. */
    public Money getOnlinePrice() {
        return onlinePrice;
    }

    public Marketplace getOnlinePriceSource() {
        return onlinePriceSource;
    }

    /** When the online price was seen, or null if there is none. */
    public LocalDate getOnlinePriceObservedOn() {
        return onlinePriceObservedOn;
    }

    /**
     * Records what this sold for online.
     *
     * <p>Latest observation wins, and an older one is ignored rather than allowed to overwrite
     * a newer: consignments are imported in whatever order the paperwork turns up, so arrival
     * order is not observation order.
     *
     * <p>This deliberately cannot make a product sellable. A marketplace price is one seller's
     * asking price on one day; MRP is the printed legal ceiling. If this were ever allowed to
     * stand in for a missing MRP it would be a way around the rule rather than an input to it
     * — {@code OnlinePriceDoesNotGrantSellabilityTest} holds that line.
     */
    public void observeOnlinePrice(Money price, Marketplace source, LocalDate observedOn) {
        Objects.requireNonNull(price, "online price");
        Objects.requireNonNull(source, "marketplace (a price cannot be judged without it)");
        Objects.requireNonNull(observedOn, "observation date (a price without one outlives it)");
        if (!price.isPositive()) {
            throw new IllegalArgumentException("online price must be positive, was " + price);
        }
        if (onlinePriceObservedOn != null && observedOn.isBefore(onlinePriceObservedOn)) {
            return;
        }
        this.onlinePrice = price;
        this.onlinePriceSource = source;
        this.onlinePriceObservedOn = observedOn;
    }

    /** What damaged units sell for, or null while nobody has decided. */
    public Money getDamagedSellingPrice() {
        return damagedSellingPrice;
    }

    /**
     * Sets what damaged units sell for.
     *
     * <p>Deliberately separate from {@link #setSellingPrice}: the two are decided at different
     * moments, and a shop that sells seconds needs to be able to reprice them without touching
     * what sound goods cost a customer.
     */
    public void setDamagedSellingPrice(Money price) {
        Objects.requireNonNull(price, "damaged selling price");
        if (!price.isPositive()) {
            throw new IllegalArgumentException(
                    "damaged selling price must be positive, was " + price);
        }
        this.damagedSellingPrice = price;
    }

    public String getHsnCode() {
        return hsnCode;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public boolean isPriceReviewFlagged() {
        return priceReviewFlagged;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
