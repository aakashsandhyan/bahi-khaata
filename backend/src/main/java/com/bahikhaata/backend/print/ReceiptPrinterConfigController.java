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
package com.bahikhaata.backend.print;

import com.bahikhaata.contracts.ReceiptPrinterConfigRequest;
import com.bahikhaata.contracts.ReceiptPrinterConfigView;
import com.bahikhaata.contracts.PrinterTestResponse;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for the ESC/POS receipt printer — the second printer, separate from
 * {@link PrinterConfigController} which drives the label printer.
 *
 * <p>GET returns the current config, PUT saves it, POST /test-print sends a real test slip and
 * records the outcome. The row seeded by the migration always exists, so there is no create path.
 */
@RestController
@RequestMapping("/api/admin/receipt-printer-config")
class ReceiptPrinterConfigController {

    private final ReceiptPrinterConfigRepository configRepo;
    private final ReceiptPrinting receiptPrinting;

    ReceiptPrinterConfigController(
            ReceiptPrinterConfigRepository configRepo, ReceiptPrinting receiptPrinting) {
        this.configRepo = configRepo;
        this.receiptPrinting = receiptPrinting;
    }

    @GetMapping
    ResponseEntity<ReceiptPrinterConfigView> getConfig() {
        return ResponseEntity.ok(toView(load()));
    }

    @PutMapping
    ResponseEntity<ReceiptPrinterConfigView> saveConfig(
            @RequestBody ReceiptPrinterConfigRequest req) {
        ReceiptPrinterConfig config = load();
        config.setAddress(req.address());
        config.setTransport(req.transport());
        config.setEnabled(req.enabled());
        configRepo.save(config);
        return ResponseEntity.ok(toView(config));
    }

    /** Sends a real test slip to the printer and records whether it answered. Never throws. */
    @PostMapping("/test-print")
    ResponseEntity<PrinterTestResponse> testPrint() {
        PrinterDriver.PrinterStatus status = receiptPrinting.test();
        ReceiptPrinterConfig config = load();
        Instant now = Instant.now();
        config.recordTest(status.connected() ? "OK" : "ERROR", status.connected() ? null : status.message(), now);
        configRepo.save(config);
        return ResponseEntity.ok(new PrinterTestResponse(
                status.connected() ? "OK" : "ERROR", status.message(), now));
    }

    private ReceiptPrinterConfig load() {
        return configRepo.findById(ReceiptPrinterConfig.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Receipt printer config row missing"));
    }

    private ReceiptPrinterConfigView toView(ReceiptPrinterConfig c) {
        return new ReceiptPrinterConfigView(
                c.getAddress(), c.getTransport(), c.isEnabled(),
                c.getTestStatus(), c.getTestError(), c.getLastTestedAt());
    }
}
