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
package com.bahikhaata.backend.config;

import com.bahikhaata.backend.inventory.allocation.AllocationWeighting;
import com.bahikhaata.backend.inventory.allocation.CostAllocator;
import com.bahikhaata.backend.inventory.allocation.RelativeMrpWeighting;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses how lot costs are apportioned.
 *
 * <p>The one place the strategy is named. Swapping it means changing this line and nothing
 * else — everything common to allocation, including exact reconciliation, lives in {@link
 * CostAllocator} and is unaffected by the choice.
 *
 * <p>Lots already allocated keep the figures the method in force at the time produced. Nothing
 * re-costs history when this changes, because a batch stores its own allocated share and the
 * lot records the method that produced it.
 */
@Configuration
class AllocationConfiguration {

    @Bean
    AllocationWeighting allocationWeighting() {
        return new RelativeMrpWeighting();
    }

    @Bean
    CostAllocator costAllocator(AllocationWeighting weighting) {
        return new CostAllocator(weighting);
    }
}
