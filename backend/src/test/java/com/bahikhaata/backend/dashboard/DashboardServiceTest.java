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
package com.bahikhaata.backend.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bahikhaata.backend.checkout.Checkout;
import com.bahikhaata.backend.inventory.ConsignmentImporter;
import com.bahikhaata.backend.inventory.ExpectedLine;
import com.bahikhaata.backend.inventory.ExpectedLineRepository;
import com.bahikhaata.backend.inventory.GoodsInCounting;
import com.bahikhaata.backend.inventory.Lot;
import com.bahikhaata.backend.inventory.LotRepository;
import com.bahikhaata.backend.inventory.Supplier;
import com.bahikhaata.backend.inventory.SupplierRepository;
import com.bahikhaata.backend.pricing.ProductCapture;
import com.bahikhaata.backend.pricing.ProductCaptureRepository;
import com.bahikhaata.backend.print.PrintJob;
import com.bahikhaata.backend.print.PrintJobRepository;
import com.bahikhaata.backend.shelf.ProductPricing;
import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.CartView;
import com.bahikhaata.contracts.DashboardAlert;
import com.bahikhaata.contracts.DashboardFunnelPoint;
import com.bahikhaata.contracts.DashboardFunnelStage;
import com.bahikhaata.contracts.DashboardView;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.PaymentMethod;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wiring and arithmetic for {@link DashboardService} against a real (scratch) database. IST
 * boundary correctness itself is proven separately, and without a Spring context, by {@link
 * IstCalendarDayTest} — this class only needs to show the service assembles the right numbers
 * from real rows.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-dashboard.db")
@Transactional
class DashboardServiceTest {

    private static final Instant AT = Instant.parse("2026-07-23T09:00:00Z");
    // Today's real IST date, so consignment-imported lots never trip the "stale open lot" alert —
    // that alert is exercised deliberately, once, by staleLot() below.
    private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();

    @Autowired private DashboardService dashboardService;
    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private ProductPricing pricing;
    @Autowired private Checkout checkout;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private LotRepository lots;
    @Autowired private SupplierRepository suppliers;
    @Autowired private ProductCaptureRepository captures;
    @Autowired private PrintJobRepository printJobs;

    private String supplierId(String name) {
        return suppliers.findByNameNormalized(Supplier.normalize(name))
                .map(Supplier::getId)
                .orElseGet(() -> suppliers.save(new Supplier(name, null, null, null, null, null)).getId())
                .toString();
    }

    private UUID importOneLine(String code, long quantity, long lotAmountPaise) {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        supplierId("Dash Supplier"), TODAY,
                        List.of(new ImportLot("HOME_ESSENTIALS", lotAmountPaise,
                                AllocationMethod.RELATIVE_MRP,
                                List.of(new ImportLine(code, code, quantity, lotAmountPaise, null,
                                        "BOX-" + code, null, null))))));
        return lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b)
                .orElseThrow().getId();
    }

    @Test
    @DisplayName("An empty database produces a whole, guard-safe payload — no divide-by-zero, no alerts")
    void emptyDatabasePayload() {
        DashboardView view = dashboardService.view();

        assertThat(view.kpis().revenueToday().totalPaise()).isZero();
        assertThat(view.kpis().revenueToday().billCount()).isZero();
        assertThat(view.kpis().revenueToday().averagePaise()).as("no bills, no average").isNull();

        assertThat(view.kpis().receivedVsPriced().receivedUnits()).isZero();
        assertThat(view.kpis().receivedVsPriced().pricedUnits()).isZero();
        assertThat(view.kpis().receivedVsPriced().unpricedBacklogUnits()).isZero();

        assertThat(view.kpis().recovery().revenuePaise()).isZero();
        assertThat(view.kpis().recovery().paidPaise()).isZero();
        assertThat(view.kpis().recovery().ratio()).as("no lots, no ratio").isNull();

        assertThat(view.kpis().gst().taxAllTimePaise()).isZero();
        assertThat(view.kpis().gst().computed()).isFalse();

        assertThat(view.funnel()).hasSize(3);
        assertThat(view.funnel()).allSatisfy(stage -> {
            assertThat(stage.units()).isZero();
            assertThat(stage.mrpPaise()).isZero();
        });

        assertThat(view.alerts()).as("zero-count signals are omitted, not shown as zero").isEmpty();
        assertThat(view.recentSales()).isEmpty();
    }

    @Test
    @DisplayName("A populated database aggregates correctly across every section")
    void populatedAggregateCorrectness() {
        // Product A: 5 received @ MRP 1000, priced at 500, then 2 sold — leaves 3 on-hand priced.
        UUID lotA = importOneLine("DASHA", 5, 10_000);
        ExpectedLine lineA = expectedLines.findByLotIdOrderByCode(lotA).get(0);
        counting.countExpected(lineA.getId(), StockCondition.GOOD, 5, Money.ofPaise(100_000), false, AT);
        UUID productA = lineA.getProduct().getId();
        pricing.setSellingPrice(productA, Money.ofPaise(50_000));

        CartView cart = checkout.open();
        cart = checkout.scan(cart.cartId(), "DASHA");
        cart = checkout.scan(cart.cartId(), "DASHA"); // quantity 2
        checkout.complete(cart.cartId(), PaymentMethod.CASH, "E2E");

        // Product B: 4 received @ MRP 600, never priced — the unpriced backlog.
        UUID lotB = importOneLine("DASHB", 4, 6_000);
        ExpectedLine lineB = expectedLines.findByLotIdOrderByCode(lotB).get(0);
        counting.countExpected(lineB.getId(), StockCondition.GOOD, 4, Money.ofPaise(60_000), false, AT);

        // Product C: 4 units counted straight into NEEDS_WORK — off-ledger, so it touches neither
        // receivedUnits nor the funnel, only the dedicated backlog query.
        UUID lotC = importOneLine("DASHC", 1, 4_000); // the manifest line's own quantity is unused
        ExpectedLine lineC = expectedLines.findByLotIdOrderByCode(lotC).get(0);
        counting.countExpected(
                lineC.getId(), StockCondition.NEEDS_WORK, 4, null, false, null, "CLEAN", AT);

        // One pending phone capture. Flushed explicitly: DashboardRepository reads through plain
        // JdbcTemplate on the same connection, which — unlike a JPQL query — never triggers
        // Hibernate's auto-flush, so a save left buffered in the persistence context would be
        // invisible to it.
        captures.saveAndFlush(new ProductCapture("Dash Capture", null, "seeded for the test", null));

        // One label held in the review queue.
        PrintJob review = PrintJob.create("DASH0001", "Dash Kettle", 50_000, 100_000L, 2, productA);
        review.setStatus("review");
        printJobs.saveAndFlush(review);

        // One lot, deliberately old and still receiving — the stale-intake alert. Its own amount
        // paid is zero so it does not disturb the recovery ratio below.
        lots.saveAndFlush(new Lot(
                "Stale Co", LocalDate.of(2000, 1, 1), Money.ZERO, Money.ZERO,
                AllocationMethod.RELATIVE_MRP));

        DashboardView view = dashboardService.view();

        // --- revenue today: the one completed sale, rung up just now, is in today's IST window ---
        assertThat(view.kpis().revenueToday().totalPaise()).isEqualTo(100_000);
        assertThat(view.kpis().revenueToday().billCount()).isEqualTo(1);
        assertThat(view.kpis().revenueToday().averagePaise()).isEqualTo(100_000);

        // --- received vs priced: 5 + 4 received (C is off-ledger); 3 on-hand priced; 4 unpriced ---
        assertThat(view.kpis().receivedVsPriced().receivedUnits()).isEqualTo(9);
        assertThat(view.kpis().receivedVsPriced().pricedUnits()).isEqualTo(3);
        assertThat(view.kpis().receivedVsPriced().unpricedBacklogUnits()).isEqualTo(4);

        // --- recovery: 100,000 revenue over 10,000 + 6,000 + 4,000 + 0 paid = 20,000 ---
        assertThat(view.kpis().recovery().revenuePaise()).isEqualTo(100_000);
        assertThat(view.kpis().recovery().paidPaise()).isEqualTo(20_000);
        assertThat(view.kpis().recovery().ratio()).isEqualTo(5.0);

        // --- gst: still not computed ---
        assertThat(view.kpis().gst().taxAllTimePaise()).isZero();
        assertThat(view.kpis().gst().computed()).isFalse();

        // --- funnel: received 9 @ (5*1000 + 4*600) MRP; priced 3 @ 3*1000; sold 2 @ 2*1000 ---
        assertThat(view.funnel())
                .extracting(DashboardFunnelPoint::stage, DashboardFunnelPoint::units, DashboardFunnelPoint::mrpPaise)
                .containsExactly(
                        tuple(DashboardFunnelStage.RECEIVED, 9L, 740_000L),
                        tuple(DashboardFunnelStage.PRICED, 3L, 300_000L),
                        tuple(DashboardFunnelStage.SOLD, 2L, 200_000L));

        // --- alerts: all five real signals present, each a real non-zero count ---
        assertThat(view.alerts())
                .extracting(DashboardAlert::signal, DashboardAlert::count, DashboardAlert::targetView)
                .containsExactlyInAnyOrder(
                        tuple("unpriced", 4L, "pricing"),
                        tuple("needs-work", 4L, "prep"),
                        tuple("captures", 1L, "review"),
                        tuple("labels-review", 1L, "review"),
                        tuple("stale-lots", 1L, "lots"));

        // --- recent sales: the one bill, through Checkout's own public API ---
        assertThat(view.recentSales()).hasSize(1);
        assertThat(view.recentSales().get(0).totalPaise()).isEqualTo(100_000);
        assertThat(view.recentSales().get(0).paymentMethod()).isEqualTo(PaymentMethod.CASH);
    }
}
