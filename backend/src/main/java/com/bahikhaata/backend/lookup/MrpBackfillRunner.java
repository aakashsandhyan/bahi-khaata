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
package com.bahikhaata.backend.lookup;

import com.bahikhaata.contracts.MrpBackfillStatus;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs a whole MRP fill in the background, so a slow look-up of every unpriced item does not hold a
 * request open for minutes.
 *
 * <p>The work list is snapshotted once and walked in small chunks, each committed on its own, so
 * progress is visible and durable as it goes. One fill runs at a time; asking again while it runs
 * just returns where it is. If several chunks in a row find nothing — the sign the source is
 * throttling or blocking — it stops early rather than hammer on.
 */
@Service
public class MrpBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(MrpBackfillRunner.class);
    private static final int CHUNK = 5;
    private static final int DRY_STREAK_STOP = 4;

    private final MrpBackfill backfill;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicReference<MrpBackfillStatus> status =
            new AtomicReference<>(new MrpBackfillStatus(false, 0, 0, 0, "Idle."));

    MrpBackfillRunner(MrpBackfill backfill) {
        this.backfill = backfill;
    }

    /** Starts a background fill if one is not already running; returns the current progress. */
    public synchronized MrpBackfillStatus start() {
        MrpBackfillStatus current = status.get();
        if (current.running()) {
            return current;
        }
        List<String> asins = backfill.waitingAsins();
        if (asins.isEmpty()) {
            MrpBackfillStatus done = new MrpBackfillStatus(false, 0, 0, 0, "Nothing waiting on a price.");
            status.set(done);
            return done;
        }
        MrpBackfillStatus started = new MrpBackfillStatus(true, asins.size(), 0, 0, "Filling…");
        status.set(started);
        worker.submit(() -> run(asins));
        return started;
    }

    public MrpBackfillStatus status() {
        return status.get();
    }

    private void run(List<String> asins) {
        int done = 0;
        int recorded = 0;
        int dryStreak = 0;
        try {
            for (int i = 0; i < asins.size(); i += CHUNK) {
                List<String> chunk = asins.subList(i, Math.min(i + CHUNK, asins.size()));
                int filled = backfill.fillChunk(chunk);
                recorded += filled;
                done += chunk.size();
                dryStreak = filled == 0 ? dryStreak + 1 : 0;
                if (dryStreak >= DRY_STREAK_STOP && recorded == 0) {
                    // Found nothing at all — the source is blocking, not the goods priceless. What
                    // it appeared to try was no fair test, so free those items to try again later.
                    backfill.forget(asins.subList(0, done));
                    status.set(
                            new MrpBackfillStatus(
                                    false, asins.size(), 0, recorded,
                                    "Stopped early — the source found nothing (likely throttling or"
                                            + " blocking). Nothing was marked tried; try again later,"
                                            + " or read the packs."));
                    return;
                }
                status.set(new MrpBackfillStatus(true, asins.size(), done, recorded, "Filling…"));
            }
            status.set(
                    new MrpBackfillStatus(
                            false, asins.size(), done, recorded,
                            "Done: " + recorded + " priced as estimates — read the pack to confirm."));
        } catch (RuntimeException e) {
            // An aborted run was not a fair test of what it had reached; free it to try again.
            backfill.forget(asins.subList(0, done));
            log.warn("Background MRP fill stopped: {}", e.getMessage());
            status.set(
                    new MrpBackfillStatus(
                            false, asins.size(), done, recorded, "Stopped: " + e.getMessage()));
        }
    }
}
