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

import java.util.List;

/**
 * A product opened from the catalogue: its states, its codes, and its catalogue standing.
 *
 * <p>The states half reuses {@link ProductStates} verbatim — name, category, and every stock pile
 * with quantities — so the catalogue detail and the remediation view stay one shape. The codes and
 * the found/priced standing are what the catalogue adds on top.
 */
public record CatalogDetail(
        ProductStates states,
        List<ProductCode> codes,
        CatalogStatus status,
        boolean priced,
        long unitsExpected,
        long unitsCounted) {}
