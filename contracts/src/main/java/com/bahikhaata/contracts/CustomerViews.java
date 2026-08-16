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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The customer surfaces. Figures are derived from customer × sale at read time, never stored.
 * The list masks mobiles to the last four digits; the full number travels only on the detail.
 */
public final class CustomerViews {
    private CustomerViews() {}

    /** The counter's lookup/attach answer — the one place a full mobile rides outside detail. */
    public record CustomerView(UUID id, String name, String mobile) {}

    /** One list row — mobile pre-masked server-side (last four digits). */
    public record CustomerRow(
            UUID id,
            String name,
            String tag,
            String mobileMasked,
            long visits,
            long spentPaise,
            Long averagePaise,
            String likes,
            Instant lastVisitAt) {}

    /** The Customers screen's stats strip. Nullable figures are honest absences, never fake zeros. */
    public record CustomerStats(
            long peopleOnFile,
            Integer repeatShareOfRevenuePercent,
            Long averageRepeatBasketPaise,
            Long averageWalkInBasketPaise,
            long lapsedSixtyDaysPlus) {}

    public record CustomerList(CustomerStats stats, List<CustomerRow> customers) {}

    /** One past visit on the detail: when, a line summary, the bill amount. */
    public record CustomerVisit(long billNo, String billNoFormatted, Instant at, String what, long amountPaise) {}

    public record CustomerDetail(
            UUID id, String name, String mobile, Instant since, List<CustomerVisit> visits) {}
}
