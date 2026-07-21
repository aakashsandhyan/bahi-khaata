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
package com.bahikhaata.backend.shelf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.backend.catalog.BarcodeRepository;
import com.bahikhaata.backend.inventory.Batch;
import com.bahikhaata.backend.inventory.BatchRepository;
import com.bahikhaata.backend.inventory.ConsignmentImporter;
import com.bahikhaata.backend.inventory.ExpectedLine;
import com.bahikhaata.backend.inventory.ExpectedLineRepository;
import com.bahikhaata.backend.inventory.GoodsInCounting;
import com.bahikhaata.backend.inventory.Lot;
import com.bahikhaata.backend.inventory.LotClosing;
import com.bahikhaata.backend.inventory.LotRepository;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.Marketplace;
import com.bahikhaata.contracts.Money;
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
    @Autowired private LotClosing closing;
    @Autowired private ShelfReadiness shelf;
    @Autowired private ProductPricing pricing;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BatchRepository batches;
    @Autowired private BarcodeRepository barcodes;
    @Autowired private LotRepository lots;

    /** Imports one line, counts it in full, and returns the lot. MRP optional. */
    private UUID receive(String code, long qty, long unitValue, Long mrpPaise) {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        "Sushil", "2026-07-17",
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
    @DisplayName("Pricing is refused while the delivery is still being unpacked")
    void cannotPriceBeforeTheLotCloses() {
        UUID lot = receive("OPEN1", 4, 10_000, 99_900L);
        UUID productId = productIn(lot);

        assertThatThrownBy(() -> pricing.setSellingPrice(productId, Money.ofPaise(50_000)))
                .as("a margin against an unknown cost is not a margin")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("still being unpacked");

        closing.close(lot, false, AT);

        pricing.setSellingPrice(productId, Money.ofPaise(50_000));
        assertThat(batchIn(lot).getProduct().getSellingPrice()).isEqualTo(Money.ofPaise(50_000));
    }

    @Test
    @DisplayName("A price above the printed MRP is refused, and exactly at it is allowed")
    void cannotPriceAboveMrp() {
        UUID lot = receive("MRP1", 2, 10_000, 99_900L);
        closing.close(lot, false, AT);
        UUID productId = productIn(lot);

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
        UUID lot = receive("GATE1", 2, 10_000, 99_900L);
        closing.close(lot, false, AT);
        UUID batchId = batchIn(lot).getId();

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
        UUID lot = receive("NOMRP", 2, 10_000, null);
        closing.close(lot, false, AT);
        UUID batchId = batchIn(lot).getId();
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
        UUID lot = receive("LABEL1", 2, 10_000, 100_000L);
        closing.close(lot, false, AT);
        pricing.setSellingPrice(productIn(lot), Money.ofPaise(75_000));

        var label = shelf.label(batchIn(lot).getId(), AT);

        assertThat(label.mrp()).isEqualTo(Money.ofPaise(100_000));
        assertThat(label.sellingPrice()).isEqualTo(Money.ofPaise(75_000));
        assertThat(label.saving()).isEqualTo(Money.ofPaise(25_000));
        assertThat(label.savingPercent()).isEqualTo(25);
    }

    @Test
    @DisplayName("The saving percentage rounds down, never flattering the discount")
    void savingPercentRoundsDown() {
        UUID lot = receive("ROUND1", 1, 10_000, 30_000L);
        closing.close(lot, false, AT);
        // 30,000 down to 20,500 is 31.66…% — the tag must say 31, not 32.
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
        closing.close(lot, false, AT);

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
    @DisplayName("A product's origin reaches back to its supplier")
    void originReachesTheSupplier() {
        UUID lot = receive("ORIGIN1", 3, 10_000, 99_900L);
        closing.close(lot, false, AT);

        Batch batch = batchIn(lot);
        assertThat(batch.getLot().getSupplier()).isEqualTo("Sushil");
        assertThat(barcodes.findByCode("ORIGIN1").orElseThrow().getProduct().getId())
                .isEqualTo(batch.getProduct().getId());
    }
}
