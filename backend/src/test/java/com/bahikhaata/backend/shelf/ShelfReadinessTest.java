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
package com.bahikhaata.backend.shelf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.backend.catalog.Barcode;
import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.backend.inventory.ConsignmentImporter;
import com.bahikhaata.backend.inventory.ExpectedLine;
import com.bahikhaata.backend.inventory.ExpectedLineRepository;
import com.bahikhaata.backend.inventory.GoodsInCounting;
import com.bahikhaata.backend.inventory.Lot;
import com.bahikhaata.backend.inventory.LotRepository;
import com.bahikhaata.backend.inventory.Supplier;
import com.bahikhaata.backend.inventory.SupplierRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.Marketplace;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.Origin;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** What must be true before goods may be sold. */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-shelf.db")
@Transactional
class ShelfReadinessTest {

    private static final Instant AT = Instant.parse("2026-07-21T09:00:00Z");

    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private ShelfReadiness shelf;
    @Autowired private ProductPricing pricing;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BatchRepository batches;
    @Autowired private BarcodeRepository barcodes;
    @Autowired private LotRepository lots;
    @Autowired private SupplierRepository suppliers;

    private String supplierId(String name) {
        return suppliers.findByNameNormalized(Supplier.normalize(name))
                .map(Supplier::getId)
                .orElseGet(() -> suppliers.save(new Supplier(name, null, null, null, null, null)).getId())
                .toString();
    }

    /** Imports one line, counts it in full, and returns the lot. MRP optional. */
    private UUID receive(String code, long qty, long unitValue, Long mrpPaise) {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Sushil"), "2026-07-17",
                        List.of(new ImportLot("KITCHEN", 100_000, AllocationMethod.RELATIVE_MRP,
                                List.of(new ImportLine(code, code, qty, unitValue, null,
                                        "BOX-" + code, null, null))))));
        UUID lotId = lots.findAll().stream().filter(Lot::isOpen)
                .reduce((a, b) -> b).orElseThrow().getId();
        ExpectedLine line = expectedLines.findByLotIdOrderByCode(lotId).get(0);
        counting.countExpected(line.getId(), qty,
                mrpPaise == null ? null : Money.ofPaise(mrpPaise), false, AT);
        return lotId;
    }

    private Batch batchIn(UUID lotId) {
        return batches.findByLotId(lotId).get(0);
    }

    private UUID productIn(UUID lotId) {
        return batchIn(lotId).getProduct().getId();
    }

    @Test
    @DisplayName("A product costed at receipt is priceable before its lot closes")
    void canPriceOnceCostedAtReceipt() {
        UUID lot = receive("OPEN1", 4, 10_000, 99_900L);
        UUID productId = productIn(lot);

        // The lot is still open, but the batch was costed from its manifest line the moment it was
        // counted, so its margin is against a known cost — pricing needs no lot close.
        assertThat(lots.findById(lot).orElseThrow().isOpen()).isTrue();
        assertThat(batchIn(lot).isCosted()).isTrue();

        pricing.setSellingPrice(productId, Money.ofPaise(50_000));
        assertThat(batchIn(lot).getProduct().getSellingPrice()).isEqualTo(Money.ofPaise(50_000));
    }

    @Test
    @DisplayName("Pricing is refused while a product's cost is still unknown")
    void cannotPriceUncostedStock() {
        // A line that states no value leaves the batch uncosted: a margin against an unknown cost
        // is not a margin, so pricing is refused until the cost is known.
        UUID lot = receive("UNCOSTED1", 4, 0, 99_900L);
        UUID productId = productIn(lot);
        assertThat(batchIn(lot).isCosted()).isFalse();

        assertThatThrownBy(() -> pricing.setSellingPrice(productId, Money.ofPaise(50_000)))
                .as("a margin against an unknown cost is not a margin")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("still being unpacked");
    }

    @Test
    @DisplayName("A price above the printed MRP is refused, and exactly at it is allowed")
    void cannotPriceAboveMrp() {
        UUID lot = receive("MRP1", 2, 10_000, 99_900L);        UUID productId = productIn(lot);

        assertThatThrownBy(() -> pricing.setSellingPrice(productId, Money.ofPaise(99_901)))
                .as("selling above the printed maximum retail price is unlawful")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unlawful");

        pricing.setSellingPrice(productId, Money.ofPaise(99_900));
        assertThat(batchIn(lot).getProduct().getSellingPrice()).isEqualTo(Money.ofPaise(99_900));
    }

    @Test
    @DisplayName("Goods are not sellable until priced, MRP-bearing and labelled")
    void allThreeAreRequired() {
        UUID lot = receive("GATE1", 2, 10_000, 99_900L);        UUID batchId = batchIn(lot).getId();

        assertThat(shelf.of(batchId).sellable()).isFalse();
        assertThat(shelf.of(batchId).missing()).containsExactly("no price set", "not labelled");

        pricing.setSellingPrice(productIn(lot), Money.ofPaise(50_000));
        assertThat(shelf.of(batchId).sellable())
                .as("priced and MRP-bearing, but nothing on the goods says so yet")
                .isFalse();
        assertThat(shelf.of(batchId).missing()).containsExactly("not labelled");

        shelf.label(batchId, AT);

        assertThat(shelf.of(batchId).sellable()).isTrue();
        assertThat(shelf.of(batchId).missing()).isEmpty();
    }

    @Test
    @DisplayName("Goods with no MRP stay off the floor and cannot be labelled")
    void noMrpNoLabel() {
        UUID lot = receive("NOMRP", 2, 10_000, null);        UUID batchId = batchIn(lot).getId();
        pricing.setSellingPrice(productIn(lot), Money.ofPaise(50_000));

        assertThat(shelf.of(batchId).hasMrp()).isFalse();
        assertThat(shelf.of(batchId).missing()).contains("no MRP read off the goods");
        assertThatThrownBy(() -> shelf.label(batchId, AT))
                .as("a label shows the saving against the printed price; without one there is"
                        + " nothing to show and nothing to check it against")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no MRP");
        assertThat(shelf.of(batchId).sellable()).isFalse();
    }

    @Test
    @DisplayName("A label carries the MRP, our price, and the saving both ways")
    void labelShowsTheSaving() {
        UUID lot = receive("LABEL1", 2, 10_000, 100_000L);        pricing.setSellingPrice(productIn(lot), Money.ofPaise(75_000));

        var label = shelf.label(batchIn(lot).getId(), AT);

        assertThat(label.mrp()).isEqualTo(Money.ofPaise(100_000));
        assertThat(label.sellingPrice()).isEqualTo(Money.ofPaise(75_000));
        assertThat(label.saving()).isEqualTo(Money.ofPaise(25_000));
        assertThat(label.savingPercent()).isEqualTo(25);
    }

    @Test
    @DisplayName("The saving percentage rounds down, never flattering the discount")
    void savingPercentRoundsDown() {
        UUID lot = receive("ROUND1", 1, 10_000, 30_000L);        // 30,000 down to 20,500 is 31.66…% — the tag must say 31, not 32.
        pricing.setSellingPrice(productIn(lot), Money.ofPaise(20_500));

        var label = shelf.label(batchIn(lot).getId(), AT);

        assertThat(label.savingPercent())
                .as("a tag claiming more saving than was given misleads the customer")
                .isEqualTo(31);
    }

    @Test
    @DisplayName("Labelling is refused while anything is still missing")
    void labellingNeedsEverythingFirst() {
        UUID lot = receive("UNPRICED", 1, 10_000, 99_900L);
        assertThatThrownBy(() -> shelf.label(batchIn(lot).getId(), AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no price set");
    }

    @Test
    @DisplayName("A scanned barcode is refused as an MRP")
    void aBarcodeIsNotAPrice() {
        UUID lot = receive("BARCODE1", 1, 10_000, null);
        Batch batch = batchIn(lot);

        // What really happened: the price field had focus, someone pulled the scanner trigger
        // out of habit, and an LED batten was recorded at an MRP of ₹6,295,047,541.
        assertThatThrownBy(() -> batch.recordMrp(Money.ofPaise(62_950_475_416_700L), false))
                .as("a junk MRP is worse than none: it puts goods out with a false legal ceiling")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scanned barcode");

        assertThat(batch.getMrp()).isNull();
    }

    @Test
    @DisplayName("An MRP wildly above the observed online price is refused")
    void anImplausibleMrpIsRefused() {
        UUID lot = receive("ONLINE1", 1, 10_000, null);
        Batch batch = batchIn(lot);
        batch.getProduct().observeOnlinePrice(
                Money.ofPaise(12_893), Marketplace.AMAZON, java.time.LocalDate.of(2026, 7, 17));

        // Typing 24900 meaning ₹249 — under the absolute limit, and still nonsense.
        assertThatThrownBy(() -> batch.recordMrp(Money.ofPaise(2_490_000), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("times what these goods sold for online");

        // A steep but real discount is left alone; refusing it would push someone to leave the
        // MRP blank, which keeps stock off the shelf.
        batch.recordMrp(Money.ofPaise(128_930), false);
        assertThat(batch.getMrp()).isEqualTo(Money.ofPaise(128_930));
    }

    @Test
    @DisplayName("A label carries a code that will still scan next month")
    void labelCarriesADurableCode() {
        UUID lot = receive("LPNONLY", 1, 10_000, 100_000L);        pricing.setSellingPrice(productIn(lot), Money.ofPaise(50_000));

        // What arrives on returns: the supplier's marketplace reference, which was never
        // scannable, and a sticker naming this one unit. Neither survives onto a shelf label.
        var product = batchIn(lot).getProduct();
        barcodes.save(new Barcode(product, "LPNBLR5Q1111111", Origin.UNIT_LABEL));

        var label = shelf.label(batchIn(lot).getId(), AT);

        assertThat(label.code())
                .as("a returns sticker dies with its unit and a marketplace reference never"
                        + " scanned, so neither can go on a label that must work next month")
                .startsWith("BBZ-");
    }

    @Test
    @DisplayName("A printed manufacturer barcode is used rather than minting our own")
    void anExistingBarcodeIsReused() {
        UUID lot = receive("HASEAN", 1, 10_000, 100_000L);        pricing.setSellingPrice(productIn(lot), Money.ofPaise(50_000));
        var product = batchIn(lot).getProduct();
        barcodes.save(new Barcode(product, "8901234567891", Origin.MANUFACTURER));

        assertThat(shelf.label(batchIn(lot).getId(), AT).code())
                .as("it is already on the goods and will be on the next delivery too")
                .isEqualTo("8901234567891");
    }

    @Test
    @DisplayName("Relabelling a product prints the same code, not a new one")
    void relabellingKeepsTheCode() {
        UUID lot = receive("SAMECODE", 2, 10_000, 100_000L);        pricing.setSellingPrice(productIn(lot), Money.ofPaise(50_000));

        String first = shelf.label(batchIn(lot).getId(), AT).code();
        String again = shelf.contentOf(batchIn(lot).getId()).code();

        assertThat(again)
                .as("two labels for one product showing different codes would resolve to the"
                        + " same thing but look like two products on a shelf")
                .isEqualTo(first);
    }

    @Test
    @DisplayName("Damaged goods need their own price before they can be labelled")
    void damagedGoodsNeedTheirOwnPrice() {
        UUID lot = receive("DAMAGED1", 2, 10_000, 100_000L);
        // The sound price is set; the scratched ones are still undecided.
        pricing.setSellingPrice(productIn(lot), Money.ofPaise(50_000));

        Batch damaged = batches.save(
                Batch.counted(batchIn(lot).getProduct(), lots.findById(lot).orElseThrow(),
                        com.bahikhaata.contracts.StockCondition.DAMAGED, 1,
                        Money.ofPaise(100_000), false));

        assertThat(shelf.of(damaged.getId()).priced())
                .as("a sound price says nothing about what the scratched ones are worth")
                .isFalse();
        assertThat(shelf.of(damaged.getId()).missing()).contains("no price set");
    }

    @Test
    @DisplayName("A product's origin reaches back to its supplier")
    void originReachesTheSupplier() {
        UUID lot = receive("ORIGIN1", 3, 10_000, 99_900L);
        Batch batch = batchIn(lot);
        assertThat(batch.getLot().getSupplier()).isEqualTo("Sushil");
        assertThat(barcodes.findByCode("ORIGIN1").orElseThrow().getProduct().getId())
                .isEqualTo(batch.getProduct().getId());
    }
}
