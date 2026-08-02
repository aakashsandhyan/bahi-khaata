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
package com.bahikhaata.backend.inventory;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * When a lot may still be corrected, and when it has become history.
 *
 * <p>A lot is editable while no stock from it has been consumed — at that point it is only a
 * data-entry record, and a mistyped amount should simply be fixable. Once any of its stock has
 * left, its allocated costs have been used to record cost of goods sold, and changing them
 * would rewrite margin history.
 *
 * <p>The freeze covers the <em>whole lot</em>, not just the batch that was consumed: the lot
 * amount is apportioned across every line, so editing it re-costs all of them. Freezing only
 * the consumed batch would let an edit silently change the cost basis of stock already sold.
 *
 * <p>Enforced here rather than by a database trigger, because the condition depends on the
 * ledger rather than on the row being edited — see design decision 16.
 */
@Service
public class LotEditPolicy {

    private final StockLedgerRepository ledger;

    LotEditPolicy(StockLedgerRepository ledger) {
        this.ledger = ledger;
    }

    /** Whether stock from this lot has been consumed, making its costs load bearing. */
    @Transactional(readOnly = true)
    public boolean isFrozen(UUID lotId) {
        return ledger.hasConsumptionFromLot(lotId);
    }

    /**
     * Guards an edit to a lot or any of its batches.
     *
     * @throws LotFrozenException if stock from the lot has already been consumed
     */
    @Transactional(readOnly = true)
    public void requireEditable(UUID lotId) {
        if (isFrozen(lotId)) {
            throw new LotFrozenException(lotId);
        }
    }
}
