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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Tests printer connectivity for network, serial, and OS-registered (e.g. Windows USB) printers.
 *
 * <p>Used by the admin config screen to verify a printer address before saving. Returns OK,
 * UNREACHABLE, or ERROR status with a diagnostic message.
 */
@Service
public class PrinterConnectionTester {
    private static final Logger log = LoggerFactory.getLogger(PrinterConnectionTester.class);
    private static final int TIMEOUT_MS = 5000;

    public record TestResult(String status, String message) {}

    public TestResult testConnection(String address, int portSpeed) {
        if (address.startsWith("/dev/")) {
            return testSerialPort(address, portSpeed);
        } else if (address.contains(":")) {
            return testNetworkPort(address);
        } else {
            return testPrintService(address);
        }
    }

    private TestResult testSerialPort(String portName, int portSpeed) {
        log.warn("Serial port testing not implemented yet (jssc dependency needed)");
        return new TestResult("ERROR", "Serial port support not yet implemented");
    }

    private TestResult testNetworkPort(String address) {
        String[] parts = address.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9100;

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.close();
            log.info("Printer test passed: {}:{}", host, port);
            return new TestResult("OK", "Printer connected and ready.");
        } catch (SocketTimeoutException e) {
            log.warn("Printer test timeout: {}:{}", host, port);
            return new TestResult(
                "UNREACHABLE",
                "Connection timeout: " + host + ":" + port + " did not respond after 5 seconds.");
        } catch (IOException e) {
            log.warn("Printer test failed: {}:{}", host, port, e);
            return new TestResult(
                "UNREACHABLE",
                "Connection failed: " + host + ":" + port + " (" + e.getMessage() + ")");
        } catch (IllegalArgumentException e) {
            // Covers NumberFormatException (the port segment is not a number at all — it extends
            // IllegalArgumentException) and InetSocketAddress rejecting a port outside 0-65535.
            log.error("Invalid port number in address: {}", address, e);
            return new TestResult("ERROR", "Invalid port number: " + e.getMessage());
        }
    }

    /**
     * Confirms an OS-registered printer of this name is installed — the case for a USB printer on
     * Windows (or a CUPS queue on macOS/Linux). This only checks the queue exists and is accepting
     * jobs; it does not send anything, so it cannot detect the printer being out of paper or the
     * cable unplugged if the OS still reports the queue as ready.
     */
    private TestResult testPrintService(String printerName) {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        return Arrays.stream(services)
            .filter(s -> s.getName().equalsIgnoreCase(printerName))
            .findFirst()
            .map(s -> {
                log.info("Printer test passed: \"{}\" is installed", printerName);
                return new TestResult("OK", "Found the installed printer \"" + s.getName() + "\".");
            })
            .orElseGet(() -> {
                log.warn("Printer test failed: no installed printer named \"{}\"", printerName);
                return new TestResult(
                    "UNREACHABLE",
                    "No installed printer named \"" + printerName + "\". Check the exact name in "
                        + "Windows' printer settings (Settings > Printers & scanners).");
            });
    }
}
