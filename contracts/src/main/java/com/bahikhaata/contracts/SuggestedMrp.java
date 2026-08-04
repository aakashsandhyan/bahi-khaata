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
 * A printed price found by looking the goods up, offered for someone to accept or ignore.
 *
 * <p>Never applied on its own. MRP is the legal maximum a customer may be charged and it is
 * printed on the pack in the operator's hand — a figure from a website is evidence about it,
 * so the person holding the goods decides.
 *
 * @param pricePaise what was found, or null if nothing was
 * @param source where it came from, in words a person can weigh
 * @param message why there is nothing, when there is nothing
 */
public record SuggestedMrp(Long pricePaise, String source, String message) {

    public static SuggestedMrp none(String message) {
        return new SuggestedMrp(null, null, message);
    }

    public boolean found() {
        return pricePaise != null;
    }
}
