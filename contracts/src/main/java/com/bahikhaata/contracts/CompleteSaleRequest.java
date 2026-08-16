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

/**
 * Completes the cart into a sale: the payment method, who was at the till (may be null), the open
 * register session the sale belongs to (null from the classic till, which sells sessionless), and
 * the captured customer — null is a walk-in, the ones who decline the counter's ask.
 */
public record CompleteSaleRequest(
        PaymentMethod paymentMethod,
        String operatorName,
        java.util.UUID registerSessionId,
        java.util.UUID customerId) {}
