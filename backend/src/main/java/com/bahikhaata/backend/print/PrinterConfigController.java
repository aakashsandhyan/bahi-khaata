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

import com.bahikhaata.contracts.PrinterConfigRequest;
import com.bahikhaata.contracts.PrinterConfigResponse;
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
 * Admin endpoints for printer configuration and health checks.
 *
 * <p>GET /api/admin/printer-config returns current config.
 * PUT /api/admin/printer-config updates config (create if not exists).
 * POST /api/admin/printer-config/test tests connectivity and updates status.
 */
@RestController
@RequestMapping("/api/admin/printer-config")
public class PrinterConfigController {
    private final PrinterConfigRepository configRepo;
    private final PrinterConnectionTester tester;

    public PrinterConfigController(PrinterConfigRepository configRepo, PrinterConnectionTester tester) {
        this.configRepo = configRepo;
        this.tester = tester;
    }

    @GetMapping
    public ResponseEntity<PrinterConfigResponse> getConfig() {
        // Before anyone has saved one, hand back the defaults rather than a 404, so the config
        // screen opens on a filled form to edit instead of an error. The same default is what
        // saving and testing already fall back to.
        return ResponseEntity.ok(
                toResponse(configRepo.getSingleton().orElseGet(PrinterConfig::createDefault)));
    }

    @PutMapping
    public ResponseEntity<PrinterConfigResponse> saveConfig(@RequestBody PrinterConfigRequest req) {
        PrinterConfig config = configRepo.getSingleton()
            .orElseGet(PrinterConfig::createDefault);

        config.setAddress(req.address());
        config.setPortSpeed(req.portSpeed());
        config.setPaperSize(req.paperSize());
        config.setCopiesDefault(req.copiesDefault());
        config.setEnabled(req.enabled());

        configRepo.save(config);

        return ResponseEntity.ok(toResponse(config));
    }

    @PostMapping("/test")
    public ResponseEntity<PrinterTestResponse> testPrinter() {
        PrinterConfig config = configRepo.getSingleton()
            .orElseGet(PrinterConfig::createDefault);

        PrinterConnectionTester.TestResult result = tester.testConnection(config.getAddress(), config.getPortSpeed());

        config.setTestStatus(result.status());
        config.setTestError(result.status().equals("OK") ? null : result.message());
        config.setLastTestedAt(Instant.now());
        configRepo.save(config);

        PrinterTestResponse resp = new PrinterTestResponse(result.status(), result.message(), Instant.now());
        return ResponseEntity.ok(resp);
    }

    private PrinterConfigResponse toResponse(PrinterConfig config) {
        return new PrinterConfigResponse(
            config.getId(),
            config.getAddress(),
            config.getPortSpeed(),
            config.getPaperSize(),
            config.getCopiesDefault(),
            config.isEnabled(),
            config.getTestStatus(),
            config.getLastTestedAt(),
            config.getTestError());
    }
}
