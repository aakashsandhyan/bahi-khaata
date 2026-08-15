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

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure boundary math for {@link IstCalendarDay} — no Spring context needed, since this is a
 * function of an {@link Instant} and nothing else.
 */
class IstCalendarDayTest {

    @Test
    @DisplayName("An IST calendar day is [previous-day 18:30 UTC, that day 18:30 UTC)")
    void windowIsIstMidnightToIstMidnight() {
        // 2026-07-24T10:00:00Z is 2026-07-24T15:30 IST — comfortably inside 2026-07-24 IST.
        IstCalendarDay.Window window =
                IstCalendarDay.windowContaining(Instant.parse("2026-07-24T10:00:00Z"));

        assertThat(window.startUtc()).isEqualTo("2026-07-23T18:30:00.000Z");
        assertThat(window.endUtc()).isEqualTo("2026-07-24T18:30:00.000Z");
    }

    @Test
    @DisplayName("A sale at 20:00 UTC (01:30 IST the next day) counts as the NEXT IST day")
    void earlyIstMorningSaleBelongsToTheNextDay() {
        // 2026-07-23T20:00:00Z is 2026-07-24T01:30 IST — just past IST midnight, but the UTC
        // calendar date is still the 23rd. A naive `date(created_at)` filter on the raw UTC text
        // would file this sale under the 23rd; the true IST day is the 24th.
        Instant earlyIstMorning = Instant.parse("2026-07-23T20:00:00Z");

        IstCalendarDay.Window window = IstCalendarDay.windowContaining(earlyIstMorning);

        assertThat(window.startUtc()).isEqualTo("2026-07-23T18:30:00.000Z");
        assertThat(window.endUtc()).isEqualTo("2026-07-24T18:30:00.000Z");
        // The instant itself falls inside its own window — that is the whole point of computing
        // the boundary from the instant rather than from a UTC calendar date.
        assertThat(earlyIstMorning.toString()).isGreaterThanOrEqualTo(window.startUtc());
        assertThat(earlyIstMorning.toString()).isLessThan(window.endUtc());
        // And this proves the naive approach would have gotten it wrong: the UTC calendar date of
        // the sale ("2026-07-23") differs from the UTC calendar date the window's IST day actually
        // starts printing under here ("2026-07-24") — same instant, two different "day"s depending
        // on which calendar you ask.
        assertThat(earlyIstMorning.toString()).startsWith("2026-07-23");
        assertThat(window.endUtc()).startsWith("2026-07-24");
    }

    @Test
    @DisplayName("Just before IST midnight still belongs to the earlier IST day")
    void justBeforeIstMidnightBelongsToTheEarlierDay() {
        // 2026-07-23T18:29:59.999Z is 2026-07-23T23:59:59.999 IST — one millisecond before IST
        // midnight, so still the 23rd IST, not the 24th.
        Instant justBefore = Instant.parse("2026-07-23T18:29:59.999Z");

        IstCalendarDay.Window window = IstCalendarDay.windowContaining(justBefore);

        assertThat(window.startUtc()).isEqualTo("2026-07-22T18:30:00.000Z");
        assertThat(window.endUtc()).isEqualTo("2026-07-23T18:30:00.000Z");
    }
}
