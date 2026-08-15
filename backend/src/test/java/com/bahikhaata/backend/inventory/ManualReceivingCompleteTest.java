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

import static org.assertj.core.api.Assertions.*;

import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A manifest lot's receiving completes when its last box goes terminal; a manual lot has no boxes
 * and so no such event — its receiving is finished by hand, through the endpoint under test here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ManualReceivingCompleteTest {

    @Autowired private LotRepository lots;
    @Autowired private MockMvc mvc;

    private Lot openLot() {
        return lots.save(new Lot(
                "Manual Supplier", LocalDate.now(), Money.ofRupees(1000), Money.ZERO,
                AllocationMethod.RELATIVE_MRP));
    }

    @Test
    @DisplayName("Marking an open lot's receiving finished sets the flag and nothing else")
    void marksOpenLotComplete() throws Exception {
        Lot lot = openLot();

        mvc.perform(MockMvcRequestBuilders.post("/api/lots/" + lot.getId() + "/receiving-complete"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        Lot after = lots.findById(lot.getId()).orElseThrow();
        assertThat(after.isReceivingComplete()).isTrue();
        assertThat(after.isOpen()).isTrue();
    }

    @Test
    @DisplayName("An already-complete lot is refused rather than silently re-marked")
    void refusesAlreadyComplete() throws Exception {
        Lot lot = openLot();
        lot.setReceivingComplete(true);
        lots.save(lot);

        mvc.perform(MockMvcRequestBuilders.post("/api/lots/" + lot.getId() + "/receiving-complete"))
                .andExpect(MockMvcResultMatchers.status().is4xxClientError());
    }
}
