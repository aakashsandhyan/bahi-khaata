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
package com.bahikhaata.backend.lookup;

import com.bahikhaata.contracts.MrpBackfillResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Filling in printed prices the goods never carried, by looking them up.
 *
 * <p>Admin, and deliberately bounded by a limit: a first run is tried small before it is trusted,
 * and each figure it finds is recorded as an estimate to be confirmed against the goods — never as a
 * price read off the pack.
 */
@RestController
@RequestMapping("/api/admin/mrp")
class MrpController {

    private final MrpBackfill backfill;

    MrpController(MrpBackfill backfill) {
        this.backfill = backfill;
    }

    @PostMapping("/backfill")
    MrpBackfillResult backfill(@RequestParam(defaultValue = "25") int limit) {
        MrpBackfill.Outcome o = backfill.run(limit);
        return new MrpBackfillResult(o.attempted(), o.recorded(), o.refused(), o.message());
    }
}
