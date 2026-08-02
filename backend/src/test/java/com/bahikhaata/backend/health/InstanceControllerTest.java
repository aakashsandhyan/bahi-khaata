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
package com.bahikhaata.backend.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The instance badge flag: on only when the sandbox launcher sets it, off for the live shop. */
class InstanceControllerTest {

    @Test
    void reportsSandboxWhenTheFlagIsSet() {
        assertThat(new InstanceController(true).instance()).containsEntry("sandbox", true);
    }

    @Test
    void reportsNotSandboxByDefault() {
        assertThat(new InstanceController(false).instance()).containsEntry("sandbox", false);
    }
}
