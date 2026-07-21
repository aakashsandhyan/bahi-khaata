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
package com.bahikhaata.contracts;

/**
 * What state goods arrived in.
 *
 * <p>Damaged stock is stock worth less, not stock lost. A scratched item still sells here, at a
 * lower price, and selling it is the business — so it carries the same cost as a clean one and
 * differs only in what it fetches.
 *
 * <p>The distinction is made by whoever opens the carton, who needs no judgement to make it: an
 * item is marked or it is not. What it is then worth is decided later, by someone who can see
 * what it cost.
 *
 * <p>Stored by name in {@code batch.condition}, which carries a {@code CHECK} constraint over
 * exactly these values; a drift test holds the two in step.
 */
public enum StockCondition {
    /** Sound goods, sold at the product's ordinary price. */
    GOOD,

    /**
     * Scratched, dented, or with its packaging opened — sold cheaper, as it is.
     *
     * <p>Ordinary stock at the ordinary cost. Only what it fetches differs, and that is decided
     * later by someone who can see what it cost.
     */
    DAMAGED,

    /**
     * Arrived fit for nothing. It cannot be sold at any price.
     *
     * <p>Costs nothing: its share is absorbed by the goods that can be sold, which carry the
     * whole amount and are priced accordingly — that being what the delivery really cost to get
     * sellable stock out of.
     *
     * <p>Never enters the stock ledger, because the ledger holds stock that exists to be sold
     * and this never became that. The batch records that it arrived, which is the trail, and
     * leaves "how much of this delivery was scrap" answerable without anything having been
     * costed differently.
     */
    UNUSABLE
}
