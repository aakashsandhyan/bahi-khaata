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
 * One "needs a decision" row: a real, non-zero count of something waiting on a person, and the
 * screen that resolves it. A signal whose count is zero never becomes a row — see
 * {@code DashboardService}.
 *
 * @param signal a stable key identifying which of the five real signals this is (e.g.
 *     {@code "unpriced"}) — not shown to the operator, just a React key on the frontend
 * @param count how many units/lots/entries this signal covers, always greater than zero
 * @param targetView the frontend {@code View} this row navigates to when clicked
 * @param message a plain sentence describing what is waiting
 */
public record DashboardAlert(String signal, long count, String targetView, String message) {}
