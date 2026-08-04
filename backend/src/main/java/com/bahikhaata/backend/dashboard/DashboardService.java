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

import com.bahikhaata.backend.checkout.Checkout;
import com.bahikhaata.contracts.DashboardAlert;
import com.bahikhaata.contracts.DashboardFunnelPoint;
import com.bahikhaata.contracts.DashboardFunnelStage;
import com.bahikhaata.contracts.DashboardKpis;
import com.bahikhaata.contracts.DashboardView;
import com.bahikhaata.contracts.GstKpi;
import com.bahikhaata.contracts.ReceivedVsPricedKpi;
import com.bahikhaata.contracts.RecoveryKpi;
import com.bahikhaata.contracts.RevenueTodayKpi;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the one payload the Dashboard screen renders: the KPI strip, the received → priced →
 * sold funnel, the real-signal alerts, and the five most recent sales.
 *
 * <p>Recent sales are read through {@link Checkout}'s own public API ({@link
 * Checkout#recentSales}), not through {@code checkout}'s repositories directly — the one place
 * this service reaches outside the tables {@link DashboardRepository} owns, and it does so
 * through the component boundary rather than around it (D3).
 */
@Service
public class DashboardService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * How many days an open lot (receiving not yet complete) can sit before it becomes a "needs a
     * decision" alert. No shop guidance yet on what "stale intake" should mean here, so this ships
     * as a named constant with a sane-looking default rather than a guess dressed up as policy;
     * revisit once the shop says what they'd actually want flagged (see design.md Open Questions).
     */
    static final int STALE_INTAKE_DAYS = 3;

    private static final int RECENT_SALES_LIMIT = 5;

    private final DashboardRepository dashboard;
    private final Checkout checkout;

    DashboardService(DashboardRepository dashboard, Checkout checkout) {
        this.dashboard = dashboard;
        this.checkout = checkout;
    }

    @Transactional(readOnly = true)
    public DashboardView view() {
        IstCalendarDay.Window today = IstCalendarDay.windowContaining(Instant.now());
        DashboardRepository.RevenueWindow revenue =
                dashboard.revenueInWindow(today.startUtc(), today.endUtc());
        DashboardRepository.LedgerStages stages = dashboard.ledgerStages();
        DashboardRepository.Recovery recovery = dashboard.recovery();

        DashboardKpis kpis = new DashboardKpis(
                revenueToday(revenue),
                new ReceivedVsPricedKpi(
                        stages.receivedUnits(), stages.pricedUnits(), stages.unpricedBacklogUnits()),
                recovery(recovery),
                // GST is not computed until the separate gst-inclusive-pricing change ships — the
                // sum is always structurally zero today, and the flag says so rather than letting
                // the tile's ₹0 be mistaken for an actual figure (D5).
                new GstKpi(dashboard.gstAllTimePaise(), false));

        List<DashboardFunnelPoint> funnel = List.of(
                new DashboardFunnelPoint(
                        DashboardFunnelStage.RECEIVED, stages.receivedUnits(), stages.receivedMrpPaise()),
                new DashboardFunnelPoint(
                        DashboardFunnelStage.PRICED, stages.pricedUnits(), stages.pricedMrpPaise()),
                new DashboardFunnelPoint(
                        DashboardFunnelStage.SOLD, stages.soldUnits(), stages.soldMrpPaise()));

        return new DashboardView(kpis, funnel, alerts(stages), checkout.recentSales(RECENT_SALES_LIMIT));
    }

    private RevenueTodayKpi revenueToday(DashboardRepository.RevenueWindow revenue) {
        Long average = revenue.billCount() == 0 ? null : revenue.totalPaise() / revenue.billCount();
        return new RevenueTodayKpi(revenue.totalPaise(), revenue.billCount(), average);
    }

    private RecoveryKpi recovery(DashboardRepository.Recovery recovery) {
        Double ratio = recovery.paidPaise() == 0
                ? null
                : recovery.revenuePaise() / (double) recovery.paidPaise();
        return new RecoveryKpi(recovery.revenuePaise(), recovery.paidPaise(), ratio);
    }

    /**
     * The five real signals of D7, each only when its count is non-zero — a zero-count signal is
     * omitted entirely rather than shown as "0 to do" (nothing to decide is not a decision).
     */
    private List<DashboardAlert> alerts(DashboardRepository.LedgerStages stages) {
        List<DashboardAlert> alerts = new ArrayList<>();

        if (stages.unpricedBacklogUnits() > 0) {
            alerts.add(new DashboardAlert(
                    "unpriced", stages.unpricedBacklogUnits(), "pricing",
                    stages.unpricedBacklogUnits() + " counted units are waiting on a shelf price."));
        }

        long needsWork = dashboard.needsWorkBacklogUnits();
        if (needsWork > 0) {
            alerts.add(new DashboardAlert(
                    "needs-work", needsWork, "prep",
                    needsWork + " units are waiting on prep work before they can be sold."));
        }

        long pendingCaptures = dashboard.pendingCaptureCount();
        if (pendingCaptures > 0) {
            alerts.add(new DashboardAlert(
                    "captures", pendingCaptures, "review",
                    pendingCaptures + " phone capture" + (pendingCaptures == 1 ? "" : "s")
                            + " waiting to be reviewed."));
        }

        // D7's design text sends this to "Reprint", but that screen is a barcode-driven reprint
        // lookup and has no view onto print_job's 'review' status — the label review queue those
        // jobs actually sit in (ReviewQueue.tsx's AwaitingLabels) renders under the 'review' view.
        // Routing here matches where an operator can actually see and act on these jobs; see the
        // deviation noted in the implementation return.
        long labelsInReview = dashboard.reviewPrintJobCount();
        if (labelsInReview > 0) {
            alerts.add(new DashboardAlert(
                    "labels-review", labelsInReview, "review",
                    labelsInReview + " label" + (labelsInReview == 1 ? "" : "s")
                            + " held in the review queue."));
        }

        long staleLots = dashboard.staleOpenLotCount(LocalDate.now(IST).minusDays(STALE_INTAKE_DAYS));
        if (staleLots > 0) {
            alerts.add(new DashboardAlert(
                    "stale-lots", staleLots, "lots",
                    staleLots + " lot" + (staleLots == 1 ? " is" : "s are")
                            + " still receiving after " + STALE_INTAKE_DAYS + "+ days."));
        }

        return alerts;
    }
}
