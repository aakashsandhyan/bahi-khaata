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
package com.bahikhaata.backend.print;

import com.bahikhaata.contracts.BillSettingsView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for the text printed on a bill — shop identity, GSTIN, title, the composition
 * declaration, footer. This is the whole GST posture: switching from a Bill of Supply to a Tax
 * Invoice is an edit here, not a code change, per the design.
 */
@RestController
@RequestMapping("/api/admin/bill-settings")
class BillSettingsController {

    private final BillSettingsRepository repo;

    BillSettingsController(BillSettingsRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    ResponseEntity<BillSettingsView> getSettings() {
        return ResponseEntity.ok(toView(load()));
    }

    @PutMapping
    ResponseEntity<BillSettingsView> saveSettings(@RequestBody BillSettingsView req) {
        BillSettings s = load();
        s.setShopName(req.shopName());
        s.setAddress(req.address());
        s.setGstin(req.gstin());
        s.setBillTitle(req.billTitle());
        s.setDeclaration(req.declaration());
        s.setFooter(req.footer());
        repo.save(s);
        return ResponseEntity.ok(toView(s));
    }

    private BillSettings load() {
        return repo.findById(BillSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Bill settings row missing"));
    }

    private BillSettingsView toView(BillSettings s) {
        return new BillSettingsView(
                s.getShopName(), s.getAddress(), s.getGstin(),
                s.getBillTitle(), s.getDeclaration(), s.getFooter());
    }
}
