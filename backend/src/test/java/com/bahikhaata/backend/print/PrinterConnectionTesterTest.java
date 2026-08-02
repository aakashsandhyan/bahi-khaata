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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrinterConnectionTesterTest {

    private final PrinterConnectionTester tester = new PrinterConnectionTester();

    @Test
    void detectsInvalidNetworkAddress() {
        PrinterConnectionTester.TestResult result = tester.testConnection("invalid.host.local:9100", 9600);

        assertEquals("UNREACHABLE", result.status());
        assertTrue(result.message().contains("Connection") || result.message().contains("failed"));
    }

    @Test
    void rejectsBadPortNumbers() {
        PrinterConnectionTester.TestResult result = tester.testConnection("192.168.1.1:99999", 9600);

        assertNotEquals("OK", result.status());
        assertEquals("ERROR", result.status(), "a port outside 0-65535 should be a clean ERROR, not an uncaught exception");
    }

    @Test
    void handlesLocalhostTimeout() {
        PrinterConnectionTester.TestResult result = tester.testConnection("127.0.0.1:65000", 9600);

        assertNotEquals("OK", result.status());
    }

    @Test
    void rejectsSerialPortsAsNotImplemented() {
        PrinterConnectionTester.TestResult result = tester.testConnection("/dev/ttyUSB0", 9600);

        assertEquals("ERROR", result.status());
        assertTrue(result.message().toLowerCase().contains("not yet implemented")
            || result.message().toLowerCase().contains("serial"));
    }

    @Test
    void testResultContainsMessage() {
        PrinterConnectionTester.TestResult result = tester.testConnection("unreachable.local:9100", 9600);

        assertNotNull(result.message());
        assertFalse(result.message().isEmpty());
    }

    @Test
    void addressWithNoColonAndNoDevPrefixIsTreatedAsAnOsPrinterName() {
        // No machine running this test has a printer literally named this — the deterministic,
        // hardware-free case. A printer that IS installed (e.g. "TSC TE244" on the shop's Windows
        // box) is a manual check, since it depends on what is actually installed there.
        PrinterConnectionTester.TestResult result =
                tester.testConnection("Definitely-Not-An-Installed-Printer-42", 9600);

        assertEquals("UNREACHABLE", result.status());
        assertTrue(result.message().contains("No installed printer"));
    }
}
