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
package com.bahikhaata.backend.health;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What this running instance is, for the UI to badge itself. The sandbox runs the same jar against
 * a throwaway copy of the database (see start-sandbox.bat); it sets {@code bahikhaata.sandbox=true}
 * so the screen can say so and nobody mistakes it for the live shop. Off by default, so the real
 * shop needs no flag.
 */
@RestController
@RequestMapping("/api/instance")
class InstanceController {

    private final boolean sandbox;

    InstanceController(@Value("${bahikhaata.sandbox:false}") boolean sandbox) {
        this.sandbox = sandbox;
    }

    @GetMapping
    Map<String, Boolean> instance() {
        return Map.of("sandbox", sandbox);
    }
}
