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
package com.bahikhaata.backend.inventory;

import java.util.UUID;

/**
 * Not enough stock on hand to satisfy a consumption.
 *
 * <p>Carries what was asked for and what is actually available, because the cashier needs to
 * be told which product is short and by how much — "cannot complete sale" alone leaves them
 * with a customer at the counter and nothing to say.
 */
public class InsufficientStockException extends RuntimeException {

    private final UUID productId;
    private final long requested;
    private final long available;

    public InsufficientStockException(UUID productId, long requested, long available) {
        super(
                "Product "
                        + productId
                        + " is short: "
                        + requested
                        + " requested, "
                        + available
                        + " on hand");
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    public UUID getProductId() {
        return productId;
    }

    public long getRequested() {
        return requested;
    }

    public long getAvailable() {
        return available;
    }
}
