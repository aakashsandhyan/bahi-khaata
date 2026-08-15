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
package com.bahikhaata.contracts;

import java.util.List;

/**
 * The whole Dashboard screen, in one payload: the KPI strip, the received → priced → sold funnel,
 * the "needs a decision" alerts, and the most recent sales. One request returns every section
 * together, so the tiles can never disagree mid-refresh.
 *
 * @param funnel the three stages, always in order: received, priced, sold
 * @param alerts only the signals with a non-zero count — see {@link DashboardAlert}
 * @param recentSales the five most recent sales, sourced from the checkout component's own public
 *     API ({@code Checkout.recentSales(5)}), never from another package's repository directly
 */
public record DashboardView(
        DashboardKpis kpis,
        List<DashboardFunnelPoint> funnel,
        List<DashboardAlert> alerts,
        List<SaleSummary> recentSales) {}
